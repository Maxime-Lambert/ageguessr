package app.ageguessr.features.people.IngestPeople;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Tuning of the people ingestion. See plans/done/people-sourcing.md for how the defaults were chosen. */
@Validated
@ConfigurationProperties("app.people.ingest")
public record IngestProperties(
        @Positive int targetPhotos,
        @Positive int pageSize,
        @Positive int maxPhotosPerPerson,
        @Positive int minYearsBetweenPhotos,
        // CirrusSearch caps srlimit at 500, and 50 already covers nearly everyone (spike).
        @Positive @Max(500) int depictsSearchLimit,
        @Positive int thumbnailWidth,
        @Positive long maxImageBytes,
        // Older people almost never have a photo with a corroborated date, and the game
        // wants recent, good quality photos: the list starts at this birth year.
        @Positive int minBirthYear,
        // Share of photos the curation is expected to approve until enough are reviewed
        // to observe it (ApprovalRate): ~43 % rejected in the spike, 0.4 to be safe.
        @DecimalMin("0.05") @DecimalMax("1.0") double expectedApprovalRate) {}
