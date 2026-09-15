package app.ageguessr.shared.ratelimit;

import app.ageguessr.shared.exceptions.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Partitions by client IP (request.getRemoteAddr(), which WebConfig's
 * ForwardedHeaderFilter already resolves from X-Forwarded-For when present, running
 * with HIGHEST_PRECEDENCE ahead of the security filter chain). Wired into
 * SecurityConfig ahead of UsernamePasswordAuthenticationFilter so a rate-limited
 * request never reaches authentication or a controller at all.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final Duration window;

    public RateLimitFilter(
            RateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Value("${app.rate-limit.auth.max-attempts}") int maxAttempts,
            @Value("${app.rate-limit.auth.window-minutes}") long windowMinutes) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = "auth:" + request.getRemoteAddr();
        if (rateLimiter.tryConsume(key, maxAttempts, window)) {
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
