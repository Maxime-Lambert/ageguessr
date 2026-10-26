package app.ageguessr.features.people.IngestPeople;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Tuning of the people ingestion. See plans/done/people-sourcing.md for how the defaults were chosen. */
@Validated
@ConfigurationProperties("app.people.ingest")
public record IngestProperties(
        @Positive int targetPeople,
        @Positive int pageSize,
        @Positive int maxPhotosPerPerson,
        @Positive int minYearsBetweenPhotos,
        // CirrusSearch caps srlimit at 500, and 50 already covers nearly everyone (spike).
        @Positive @Max(500) int depictsSearchLimit,
        @Positive int thumbnailWidth,
        @Positive long maxImageBytes) {}
