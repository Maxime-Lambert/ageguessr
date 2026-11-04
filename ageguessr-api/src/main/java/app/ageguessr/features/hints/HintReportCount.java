package app.ageguessr.features.hints;

import app.ageguessr.shared.reports.ReportTally;
import java.time.Instant;
import java.util.UUID;

/** The open reports of a hint for one reason: how many, and since when. */
public record HintReportCount(UUID hintId, HintReportReason reason, long count, Instant firstAt) {

    public ReportTally.Count<HintReportReason> asCount() {
        return new ReportTally.Count<>(hintId, reason, count, firstAt);
    }
}
