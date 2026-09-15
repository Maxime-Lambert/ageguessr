package app.ageguessr.shared.ratelimit;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Fixed-window counter backed by Redis (INCR + EXPIRE) — not a smoother algorithm like
 * a token bucket, so a client can burst up to ~2x the limit right at a window boundary.
 * Accepted trade-off: this defends against scripted brute forcing, it doesn't shape
 * traffic. Chosen over a library (e.g. Bucket4j) because its current Redis integration
 * needs several JDK-version-suffixed artifacts plus a separate Spring Boot starter for
 * wiring — real complexity for a rule this simple, when StringRedisTemplate (already
 * provided by spring-boot-starter-data-redis) does the job in a few lines.
 */
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate redisTemplate;

    public boolean tryConsume(String key, int maxAttempts, Duration window) {
        String redisKey = "ratelimit:" + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(redisKey, window);
        }
        return count != null && count <= maxAttempts;
    }
}
