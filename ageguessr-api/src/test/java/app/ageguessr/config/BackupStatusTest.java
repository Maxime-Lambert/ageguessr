package app.ageguessr.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupStatusTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path dir;

    private BackupStatus status(Path file) {
        return new BackupStatus(file == null ? "" : file.toString(), Duration.ofHours(26), CLOCK);
    }

    private Path written(String content) throws IOException {
        return Files.writeString(dir.resolve("last-success"), content);
    }

    @Test
    void isFreshWithinTwentySixHoursOfTheLastDump() throws IOException {
        long sevenHoursAgo = NOW.minus(Duration.ofHours(7)).getEpochSecond();
        assertThat(status(written(sevenHoursAgo + "\n")).fresh()).isTrue();
    }

    @Test
    void isStaleAfterTwentySixHours() throws IOException {
        long tooOld = NOW.minus(Duration.ofHours(26)).getEpochSecond();
        assertThat(status(written(Long.toString(tooOld))).fresh()).isFalse();
    }

    @Test
    void isStaleWithoutAFileOrWithAnUnreadableOne() throws IOException {
        assertThat(status(null).fresh()).isFalse();
        assertThat(status(dir.resolve("missing")).fresh()).isFalse();
        assertThat(status(written("not a time")).fresh()).isFalse();
    }
}
