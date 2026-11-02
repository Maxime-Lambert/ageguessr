package app.ageguessr.features.game;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Playing without an account: the guest cookie and how long a guest's games are kept. */
@Validated
@ConfigurationProperties("app.game.guest")
public record GuestProperties(
        // Long enough to come back the next days with the same id; the games themselves
        // are purged sooner, see retentionDays.
        @Positive int cookieTtlDays,
        // A guest's games are deleted this many days after the day played: enough to
        // show the result of the last days, short enough to keep nothing that serves no
        // purpose (GDPR storage limitation). An account keeps its games with it.
        @Positive int retentionDays,
        // Off in tests and in the one-off commands, like the draw.
        boolean purgeEnabled) {}
