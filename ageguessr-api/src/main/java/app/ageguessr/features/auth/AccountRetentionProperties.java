package app.ageguessr.features.auth;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How long an account is kept without being used (GDPR storage limitation). To keep in step
 * with /privacy, section 4.
 */
@Validated
@ConfigurationProperties("app.auth.retention")
public record AccountRetentionProperties(
        // An email never confirmed: the account cannot sign in and holds an email and a
        // username that someone else may want.
        @Positive int unverifiedDays,
        // No sign-in nor session renewal for this long: the account and all it holds go.
        @Positive int inactiveDays,
        // The warning email comes this many days before, and the deletion waits as long after it.
        @Positive int warningDays,
        // Off in tests and in the one-off commands, like the purges of the game.
        boolean purgeEnabled) {}
