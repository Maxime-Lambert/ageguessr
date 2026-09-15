package app.ageguessr.integration.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.shared.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Runs against a real Redis container — a mocked StringRedisTemplate (see
 * RateLimiterTest) can't catch a genuine TTL/serialization surprise.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RateLimiterIntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void blocksOnceTheThresholdIsExceeded() {
        String key = "test:" + UUID.randomUUID();

        assertThat(rateLimiter.tryConsume(key, 3, Duration.ofSeconds(5))).isTrue();
        assertThat(rateLimiter.tryConsume(key, 3, Duration.ofSeconds(5))).isTrue();
        assertThat(rateLimiter.tryConsume(key, 3, Duration.ofSeconds(5))).isTrue();
        assertThat(rateLimiter.tryConsume(key, 3, Duration.ofSeconds(5))).isFalse();
    }

    @Test
    void resetsAfterTheWindowExpires() throws InterruptedException {
        String key = "test:" + UUID.randomUUID();

        assertThat(rateLimiter.tryConsume(key, 1, Duration.ofSeconds(2))).isTrue();
        assertThat(rateLimiter.tryConsume(key, 1, Duration.ofSeconds(2))).isFalse();

        Thread.sleep(2_200);

        assertThat(rateLimiter.tryConsume(key, 1, Duration.ofSeconds(2))).isTrue();
    }

    @Test
    void partitionsIndependentlyByKey() {
        String keyA = "test:" + UUID.randomUUID();
        String keyB = "test:" + UUID.randomUUID();

        assertThat(rateLimiter.tryConsume(keyA, 1, Duration.ofSeconds(5))).isTrue();
        assertThat(rateLimiter.tryConsume(keyA, 1, Duration.ofSeconds(5))).isFalse();
        assertThat(rateLimiter.tryConsume(keyB, 1, Duration.ofSeconds(5))).isTrue();
    }
}
