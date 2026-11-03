package app.ageguessr.features.game.PurgeSeries;

import app.ageguessr.features.game.FreeSeriesRepository;
import app.ageguessr.features.game.SeriesProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the series past their retention: a series never shared after 7 days, a
 * challenge 30 days after it was shared, with its leaderboard and the nicknames of its
 * guests (V15 cascades). Nothing of a series is worth keeping beyond that (GDPR storage
 * limitation).
 */
@Service
@RequiredArgsConstructor
public class PurgeSeriesHandler {

    private final FreeSeriesRepository series;
    private final SeriesProperties properties;
    private final Clock clock;

    /** @return the number of series deleted */
    @Transactional
    public int purge() {
        Instant now = clock.instant();
        return series.deleteExpired(
                now.minus(Duration.ofDays(properties.retentionDays())),
                now.minus(Duration.ofDays(properties.challengeRetentionDays())));
    }
}
