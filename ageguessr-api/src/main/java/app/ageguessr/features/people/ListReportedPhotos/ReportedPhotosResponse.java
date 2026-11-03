package app.ageguessr.features.people.ListReportedPhotos;

import app.ageguessr.features.people.PhotoReportReason;
import app.ageguessr.features.people.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The photos players reported, most reported first. Admin only. */
public record ReportedPhotosResponse(List<Item> items) {

    /**
     * @param reasons the open reports by reason
     * @param inTodaysGame every player sees it today: the first to deal with
     */
    public record Item(
            UUID photoId,
            ReviewStatus reviewStatus,
            int ageAtPhoto,
            String sourceUrl,
            PersonView person,
            Map<PhotoReportReason, Long> reasons,
            long total,
            Instant firstReportedAt,
            boolean inTodaysGame) {}

    public record PersonView(UUID id, String wikidataId, String nameEn, String nameFr) {}
}
