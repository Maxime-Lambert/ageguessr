package app.ageguessr.features.game;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyRoundRepository extends JpaRepository<DailyRound, DailyRoundId> {

    List<DailyRound> findByGameDateOrderByPosition(LocalDate gameDate);

    /**
     * The rounds of a day, in order. A round is neutralized when its photo is gone
     * (excluded person) or no longer approved; the excluded check guards the gap in case
     * the V4 trigger is ever bypassed, as in ReviewPhotoHandler.
     */
    @Query(
            """
            SELECT new app.ageguessr.features.game.RoundState(
                r.position, r.photoId,
                CASE WHEN ph.reviewStatus = app.ageguessr.features.people.ReviewStatus.APPROVED
                          AND p.excluded = false
                     THEN app.ageguessr.features.game.DailyRoundStatus.PLAYABLE
                     ELSE app.ageguessr.features.game.DailyRoundStatus.NEUTRALIZED END)
            FROM DailyRound r
            LEFT JOIN app.ageguessr.features.people.PersonPhoto ph ON ph.id = r.photoId
            LEFT JOIN app.ageguessr.features.people.Person p ON p.id = ph.personId
            WHERE r.gameDate = :gameDate
            ORDER BY r.position
            """)
    List<RoundState> findStatesOf(@Param("gameDate") LocalDate gameDate);
}
