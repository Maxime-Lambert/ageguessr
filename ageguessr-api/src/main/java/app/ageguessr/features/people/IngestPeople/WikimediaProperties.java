package app.ageguessr.features.people.IngestPeople;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated at startup: a missing key fails with its name instead of a zero timeout deep in the HTTP client. */
@Validated
@ConfigurationProperties("app.wikimedia")
public record WikimediaProperties(
        @NotBlank String userAgent,
        @NotNull URI qleverUrl,
        @NotNull URI commonsApiUrl,
        @PositiveOrZero long requestPauseMs,
        @Positive int maxAttempts,
        @Positive int connectTimeoutSeconds,
        @Positive int readTimeoutSeconds) {}
