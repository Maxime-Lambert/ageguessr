package app.ageguessr.features.hints.DismissHintReports;

import app.ageguessr.features.hints.HintReportRepository;
import app.ageguessr.shared.reports.ReportStatus;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Keep": the admin looked at the hint and leaves it as it is. Its open reports are
 * closed, so it leaves the queue until it is reported again. Sending it twice, or for a
 * hint without open reports, changes nothing.
 */
@Service
@RequiredArgsConstructor
public class DismissHintReportsHandler {

    private final HintReportRepository reports;
    private final Clock clock;

    @Transactional
    public void handle(UUID hintId) {
        reports.closeOpen(hintId, ReportStatus.DISMISSED, clock.instant());
    }
}
