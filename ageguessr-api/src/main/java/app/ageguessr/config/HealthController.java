package app.ageguessr.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public health checks, watched by UptimeRobot every 5 minutes and by the deploy smoke
 * test (docs/decisions/architecture.md, « Monitoring »). Each answers 200 "OK" or 503
 * "UNAVAILABLE", never which part failed: anyone can call them.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController {

    private static final ResponseEntity<String> UNAVAILABLE =
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("UNAVAILABLE");

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final BackupStatus backupStatus;

    /** Up only when PostgreSQL and Redis both answer: without them no request works. */
    @GetMapping("/api/health")
    ResponseEntity<String> health() {
        return dependenciesAnswer() ? ResponseEntity.ok("OK") : UNAVAILABLE;
    }

    /** Up when the last successful database dump is recent (the backup service writes it). */
    @GetMapping("/api/health/backup")
    ResponseEntity<String> backup() {
        return backupStatus.fresh() ? ResponseEntity.ok("OK") : UNAVAILABLE;
    }

    // WARN, not ERROR: UptimeRobot already alerts on the 503, an error email every 5
    // minutes on top would only repeat it.
    private boolean dependenciesAnswer() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            String pong = redis.execute((RedisCallback<String>) RedisConnection::ping);
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            log.warn("Health check failed: {}", e.toString());
            return false;
        }
    }
}
