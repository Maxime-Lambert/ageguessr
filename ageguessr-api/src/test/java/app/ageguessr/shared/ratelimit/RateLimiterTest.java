package app.ageguessr.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void allowsRequestsUnderTheLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:auth:1.2.3.4")).thenReturn(1L);

        RateLimiter rateLimiter = new RateLimiter(redisTemplate);
        boolean allowed = rateLimiter.tryConsume("auth:1.2.3.4", 20, Duration.ofMinutes(1));

        assertThat(allowed).isTrue();
    }

    @Test
    void setsExpiryOnlyOnTheFirstHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:auth:1.2.3.4")).thenReturn(1L);

        RateLimiter rateLimiter = new RateLimiter(redisTemplate);
        rateLimiter.tryConsume("auth:1.2.3.4", 20, Duration.ofMinutes(1));

        verify(redisTemplate).expire(eq("ratelimit:auth:1.2.3.4"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void doesNotResetExpiryOnSubsequentHits() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:auth:1.2.3.4")).thenReturn(2L);

        RateLimiter rateLimiter = new RateLimiter(redisTemplate);
        rateLimiter.tryConsume("auth:1.2.3.4", 20, Duration.ofMinutes(1));

        verify(redisTemplate).opsForValue();
        verifyNoMoreInteractions(redisTemplate);
    }

    @Test
    void rejectsRequestsOverTheLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(any())).thenReturn(21L);

        RateLimiter rateLimiter = new RateLimiter(redisTemplate);
        boolean allowed = rateLimiter.tryConsume("auth:1.2.3.4", 20, Duration.ofMinutes(1));

        assertThat(allowed).isFalse();
    }
}
