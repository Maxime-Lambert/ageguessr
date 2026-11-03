package app.ageguessr.features.hints;

import app.ageguessr.shared.reports.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HintReportRepository extends JpaRepository<HintReport, UUID> {

    long countByHintIdAndStatus(UUID hintId, ReportStatus status);

    /** The open reports of every hint, by reason: the admin queue is built from them. */
    @Query(
            """
            SELECT new app.ageguessr.features.hints.HintReportCount(r.hintId, r.reason, COUNT(r), MIN(r.createdAt))
            FROM HintReport r
            WHERE r.status = app.ageguessr.shared.reports.ReportStatus.OPEN
            GROUP BY r.hintId, r.reason
            """)
    List<HintReportCount> countOpen();

    /** Closes the open reports of a hint, once the admin has dealt with it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            UPDATE HintReport r SET r.status = :status, r.closedAt = :now
            WHERE r.hintId = :hintId AND r.status = app.ageguessr.shared.reports.ReportStatus.OPEN
            """)
    int closeOpen(@Param("hintId") UUID hintId, @Param("status") ReportStatus status, @Param("now") Instant now);
}
