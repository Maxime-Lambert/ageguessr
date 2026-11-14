package app.ageguessr.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether the database backup still runs. The backup service (deploy/backup/pg-backup.sh)
 * writes the epoch second of its last successful dump to a file of a volume mounted
 * read-only here: the backend sees that time, never the dumps. No file, an unreadable one
 * or an old one: not fresh. Dumps run daily at 03:00 UTC, so 26 hours leaves room for one.
 */
@Component
class BackupStatus {

    private final String statusFile;
    private final Duration maxAge;
    private final Clock clock;

    BackupStatus(
            @Value("${app.backup.status-file:}") String statusFile,
            @Value("${app.backup.max-age:26h}") Duration maxAge,
            Clock clock) {
        this.statusFile = statusFile;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    boolean fresh() {
        if (statusFile.isBlank()) {
            return false;
        }
        try {
            Instant lastSuccess = Instant.ofEpochSecond(
                    Long.parseLong(Files.readString(Path.of(statusFile)).trim()));
            return Duration.between(lastSuccess, clock.instant()).compareTo(maxAge) < 0;
        } catch (IOException | NumberFormatException e) {
            return false;
        }
    }
}
