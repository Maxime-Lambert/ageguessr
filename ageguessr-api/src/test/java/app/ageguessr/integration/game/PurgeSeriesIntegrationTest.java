package app.ageguessr.integration.game;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesRepository;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.PurgeSeries.PurgeSeriesHandler;
import app.ageguessr.features.game.ShareCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** The retention of the free series (7 and 30 days in the test properties, as in prod). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PurgeSeriesIntegrationTest {

    private static final Duration A_MINUTE = Duration.ofMinutes(1);

    @Autowired
    private PurgeSeriesHandler handler;

    @Autowired
    private FreeSeriesRepository series;

    @Autowired
    private Clock clock;

    @Test
    void deletesASeriesNeverShared7DaysAfterItWasCreated() {
        Instant now = clock.instant();
        FreeSeries expired = save(now.minus(Duration.ofDays(7)).minus(A_MINUTE), null);
        FreeSeries kept = save(now.minus(Duration.ofDays(7)).plus(A_MINUTE), null);

        assertThat(handler.purge()).isEqualTo(1);

        assertThat(series.findById(expired.getId())).isEmpty();
        assertThat(series.findById(kept.getId())).isPresent();
    }

    @Test
    void deletesAChallenge30DaysAfterItWasSharedWhateverItsAge() {
        Instant now = clock.instant();
        Instant longAgo = now.minus(Duration.ofDays(40));
        FreeSeries expired = save(longAgo, now.minus(Duration.ofDays(30)).minus(A_MINUTE));
        FreeSeries kept = save(longAgo, now.minus(Duration.ofDays(30)).plus(A_MINUTE));

        assertThat(handler.purge()).isEqualTo(1);

        assertThat(series.findById(expired.getId())).isEmpty();
        assertThat(series.findById(kept.getId())).isPresent();
    }

    private FreeSeries save(Instant createdAt, Instant sharedAt) {
        FreeSeries created = FreeSeries.create(
                PlayerKey.guest(UUID.randomUUID()), 5, createdAt, LocalDate.ofInstant(createdAt, ZoneOffset.UTC));
        if (sharedAt != null) {
            created.share(new ShareCodes().next(), sharedAt);
        }
        return series.saveAndFlush(created);
    }
}
