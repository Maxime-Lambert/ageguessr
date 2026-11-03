package app.ageguessr.features.people;

import app.ageguessr.shared.reports.ReportTally;
import java.time.Instant;
import java.util.UUID;

/** The open reports of a photo for one reason: how many, and since when. */
public record PhotoReportCount(UUID photoId, PhotoReportReason reason, long count, Instant firstAt) {

    public ReportTally.Count<PhotoReportReason> asCount() {
        return new ReportTally.Count<>(photoId, reason, count, firstAt);
    }
}
