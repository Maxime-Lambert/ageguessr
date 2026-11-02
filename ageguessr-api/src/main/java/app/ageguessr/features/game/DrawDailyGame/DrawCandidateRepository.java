package app.ageguessr.features.game.DrawDailyGame;

import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundId;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The photos the draw may pick: approved, of people not excluded, with their last day played. */
public interface DrawCandidateRepository extends Repository<DailyRound, DailyRoundId> {

    @Query(
            """
            SELECT new app.ageguessr.features.game.DrawDailyGame.DrawCandidate(
                ph.id, p.gender, p.continent, MAX(r.gameDate))
            FROM app.ageguessr.features.people.PersonPhoto ph
            JOIN app.ageguessr.features.people.Person p ON p.id = ph.personId
            LEFT JOIN DailyRound r ON r.photoId = ph.id
            WHERE ph.reviewStatus = app.ageguessr.features.people.ReviewStatus.APPROVED
              AND p.excluded = false
            GROUP BY ph.id, p.gender, p.continent
            """)
    List<DrawCandidate> findCandidates();
}
