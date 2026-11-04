package app.ageguessr.features.people.DismissPhotoReports;

import app.ageguessr.features.people.PhotoReportRepository;
import app.ageguessr.shared.reports.ReportStatus;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Keep": the admin looked at the photo and leaves it as it is. Its open reports are
 * closed, so it leaves the queue until it is reported again. Sending it twice, or for a
 * photo without open reports, changes nothing.
 */
@Service
@RequiredArgsConstructor
public class DismissPhotoReportsHandler {

    private final PhotoReportRepository reports;
    private final Clock clock;

    @Transactional
    public void handle(UUID photoId) {
        reports.closeOpen(photoId, ReportStatus.DISMISSED, clock.instant());
    }
}
