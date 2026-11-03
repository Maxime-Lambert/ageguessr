package app.ageguessr.features.hints.ListReportedHints;

import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintReportReason;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The hints players reported, most reported first. Admin only. */
public record ReportedHintsResponse(List<Item> items) {

    /** @param reasons the open reports by reason */
    public record Item(
            UUID hintId,
            HintReviewStatus reviewStatus,
            HintCategory category,
            int year,
            HintAudience audience,
            String textEn,
            String textFr,
            String sourceUrl,
            Map<HintReportReason, Long> reasons,
            long total,
            Instant firstReportedAt) {}
}
