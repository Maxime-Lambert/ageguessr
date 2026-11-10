package app.ageguessr.shared.ratelimit;

import app.ageguessr.shared.exceptions.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Four limits, each with its own counter: every /api/auth/ request (brute forcing of
 * passwords and tokens), the guesses of the game (POST /api/game/), which a script could
 * otherwise send by the thousand, each new guest adding rows, and, tighter, the creation
 * of free series and the joining of challenges: each series reveals the ages of its
 * photos, and a script must not harvest the pool. The reports of a round have their own,
 * so that reporting never uses up the guesses, and a script cannot flood the admin queue.
 * Friends too, on what a script would try in bulk: usernames (sending a request), invite
 * codes (reading or accepting one), and every other change to the graph.
 * The game reads (GET) are not limited: they write nothing once the day is drawn.
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
    private final Limit report;
    private final Limit friends;

    public RateLimitFilter(
            RateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.auth.max-attempts}") int authMaxAttempts,
            @Value("${app.rate-limit.auth.window-minutes}") long authWindowMinutes,
            @Value("${app.rate-limit.game.max-attempts}") int gameMaxAttempts,
            @Value("${app.rate-limit.game.window-minutes}") long gameWindowMinutes,
            @Value("${app.rate-limit.series.max-attempts}") int seriesMaxAttempts,
            @Value("${app.rate-limit.series.window-minutes}") long seriesWindowMinutes,
            @Value("${app.rate-limit.report.max-attempts}") int reportMaxAttempts,
            @Value("${app.rate-limit.report.window-minutes}") long reportWindowMinutes,
            @Value("${app.rate-limit.friends.max-attempts}") int friendsMaxAttempts,
            @Value("${app.rate-limit.friends.window-minutes}") long friendsWindowMinutes) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.auth = new Limit("auth", authMaxAttempts, Duration.ofMinutes(authWindowMinutes));
        this.game = new Limit("game", gameMaxAttempts, Duration.ofMinutes(gameWindowMinutes));
        this.series = new Limit("series", seriesMaxAttempts, Duration.ofMinutes(seriesWindowMinutes));
        this.report = new Limit("report", reportMaxAttempts, Duration.ofMinutes(reportWindowMinutes));
        this.friends = new Limit("friends", friendsMaxAttempts, Duration.ofMinutes(friendsWindowMinutes));
    }

    private static final Pattern CHALLENGE_JOIN = Pattern.compile("^/api/game/challenges/[^/]+/join$");
    private static final Pattern ROUND_REPORT = Pattern.compile("^/api/game/.+/rounds/[^/]+/report$");
    private static final Set<String> FRIENDS_SCORES =
            Set.of("/api/friends/today", "/api/friends/leaderboard");
    // Someone's code, not the caller's own GET/POST /api/friends/invite.
    private static final Pattern INVITE_CODE = Pattern.compile("^/api/friends/invite/[^/]+(/accept)?$");

    /** @param name prefixes the counter, so that playing never uses up the login attempts */
    private record Limit(String name, int maxAttempts, Duration window) {}

    private Limit limitOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/")) {
            return auth;
        }
        // The costly reads: every round the account ever played, or those of the account
        // and all its friends. They share the guesses' counter.
        if (uri.equals("/api/game/history") || FRIENDS_SCORES.contains(uri)) {
            return game;
        }
        if (INVITE_CODE.matcher(uri).matches()) {
            return friends;
        }
        // Every change to the friends graph, the caller's own invite included: a request
        // sent, accepted, declined or cancelled, a friend removed, a code replaced.
        if (uri.startsWith("/api/friends") && !HttpMethod.GET.matches(request.getMethod())) {
            return friends;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        if (uri.equals("/api/game/series") || CHALLENGE_JOIN.matcher(uri).matches()) {
            return series;
        }
        if (ROUND_REPORT.matcher(uri).matches()) {
            return report;
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
