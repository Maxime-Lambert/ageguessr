package app.ageguessr.features.people;

import app.ageguessr.shared.reports.ReportStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoReportRepository extends JpaRepository<PhotoReport, UUID> {

    long countByPhotoIdAndStatus(UUID photoId, ReportStatus status);

    long countByPhotoIdAndReasonAndStatus(UUID photoId, PhotoReportReason reason, ReportStatus status);

    /** The open reports of every photo, by reason: the admin queue is built from them. */
    @Query(
            """
            SELECT new app.ageguessr.features.people.PhotoReportCount(r.photoId, r.reason, COUNT(r), MIN(r.createdAt))
            FROM PhotoReport r
            WHERE r.status = app.ageguessr.shared.reports.ReportStatus.OPEN
            GROUP BY r.photoId, r.reason
            """)
    List<PhotoReportCount> countOpen();

    /**
     * The photos of a day's game, read here so that the queue can flag them without the
     * people feature depending on the game (daily_rounds, V12).
     */
    @Query(value = "SELECT photo_id FROM daily_rounds WHERE game_date = :day AND photo_id IS NOT NULL", nativeQuery = true)
    List<UUID> findPhotosPlayedOn(@Param("day") LocalDate day);

    /** Closes the open reports of a photo, once the admin has dealt with it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            """
            UPDATE PhotoReport r SET r.status = :status, r.closedAt = :now
            WHERE r.photoId = :photoId AND r.status = app.ageguessr.shared.reports.ReportStatus.OPEN
            """)
    int closeOpen(@Param("photoId") UUID photoId, @Param("status") ReportStatus status, @Param("now") Instant now);
}
