package app.ageguessr.features.friends;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The limits of the friends graph (user decision, 2026-11-10). */
@Validated
@ConfigurationProperties("app.friends")
public record FriendsProperties(
        // Enough for any real circle; bounds what the comparison and leaderboard compute.
        @Positive int maxFriends,
        // Outgoing requests still waiting: one account cannot spam the whole site.
        @Positive int maxPendingRequests) {}
