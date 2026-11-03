package app.ageguessr.shared.ratelimit;

import app.ageguessr.shared.exceptions.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Three limits, each with its own counter: every /api/auth/ request (brute forcing of
 * passwords and tokens), the guesses of the game (POST /api/game/), which a script could
 * otherwise send by the thousand, each new guest adding rows, and, tighter, the creation
 * of free series and the joining of challenges: each series reveals the ages of its
 * photos, and a script must not harvest the pool. The game reads (GET) are not limited:
 * they write nothing once the day is drawn.
 *
 * <p>Partitions by client IP (request.getRemoteAddr(), which WebConfig's
 * ForwardedHeaderFilter already resolves from X-Forwarded-For when present, running
 * with HIGHEST_PRECEDENCE ahead of the security filter chain). Wired into
 * SecurityConfig ahead of UsernamePasswordAuthenticationFilter so a rate-limited
 * request never reaches authentication or a controller at all.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Limit auth;
    private final Limit game;
    private final Limit series;

    public RateLimitFilter(
            RateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.auth.max-attempts}") int authMaxAttempts,
            @Value("${app.rate-limit.auth.window-minutes}") long authWindowMinutes,
            @Value("${app.rate-limit.game.max-attempts}") int gameMaxAttempts,
            @Value("${app.rate-limit.game.window-minutes}") long gameWindowMinutes,
            @Value("${app.rate-limit.series.max-attempts}") int seriesMaxAttempts,
            @Value("${app.rate-limit.series.window-minutes}") long seriesWindowMinutes) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.auth = new Limit("auth", authMaxAttempts, Duration.ofMinutes(authWindowMinutes));
        this.game = new Limit("game", gameMaxAttempts, Duration.ofMinutes(gameWindowMinutes));
        this.series = new Limit("series", seriesMaxAttempts, Duration.ofMinutes(seriesWindowMinutes));
    }

    private static final Pattern CHALLENGE_JOIN = Pattern.compile("^/api/game/challenges/[^/]+/join$");

    /** @param name prefixes the counter, so that playing never uses up the login attempts */
    private record Limit(String name, int maxAttempts, Duration window) {}

    private Limit limitOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/")) {
            return auth;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        if (uri.equals("/api/game/series") || CHALLENGE_JOIN.matcher(uri).matches()) {
            return series;
        }
        if (uri.startsWith("/api/game/")) {
            return game;
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitOf(request) == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Limit limit = limitOf(request);
        String key = limit.name() + ":" + request.getRemoteAddr();
        if (rateLimiter.tryConsume(key, limit.maxAttempts(), limit.window())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse("RATE_LIMITED", "Too many requests, try again later"));
    }
}
