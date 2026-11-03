package app.ageguessr.features.game;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreeSeriesRoundRepository extends JpaRepository<FreeSeriesRound, FreeSeriesRoundId> {

    /** As {@link DailyRoundRepository#findStatesOf}: neutralized when the photo is gone or no longer approved. */
    @Query(
            """
            SELECT new app.ageguessr.features.game.DailyRoundRepository$RoundState(
                r.position, r.photoId,
                CASE WHEN ph.reviewStatus = app.ageguessr.features.people.ReviewStatus.APPROVED
                          AND p.excluded = false
                     THEN app.ageguessr.features.game.DailyRoundStatus.PLAYABLE
                     ELSE app.ageguessr.features.game.DailyRoundStatus.NEUTRALIZED END)
            FROM FreeSeriesRound r
            LEFT JOIN app.ageguessr.features.people.PersonPhoto ph ON ph.id = r.photoId
            LEFT JOIN app.ageguessr.features.people.Person p ON p.id = ph.personId
            WHERE r.seriesId = :seriesId
            ORDER BY r.position
            """)
    List<DailyRoundRepository.RoundState> findStatesOf(@Param("seriesId") UUID seriesId);
}
