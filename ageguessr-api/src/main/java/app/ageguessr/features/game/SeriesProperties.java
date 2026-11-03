package app.ageguessr.features.game;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** How long free series and challenges are kept (user decision, 2026-11-03). */
@Validated
@ConfigurationProperties("app.game.series")
public record SeriesProperties(
        // A series never shared: long enough to finish it, the same as a guest's games.
        @Positive int retentionDays,
        // A challenge, with its leaderboard and the nicknames of its guests: counted from
        // the day it was shared, long enough for friends to play it.
        @Positive int challengeRetentionDays,
        // Off in tests and in the one-off commands, like the guest purge.
        boolean purgeEnabled) {}
