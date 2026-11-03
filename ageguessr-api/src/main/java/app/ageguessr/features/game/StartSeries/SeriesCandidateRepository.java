package app.ageguessr.features.game.StartSeries;

import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.PlayerKey;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** What the draw of a series reads: the pool, today's photos, and what a player has seen. */
public interface SeriesCandidateRepository extends Repository<FreeSeries, UUID> {

    /** Approved photos of people not excluded: the same pool as the daily draw. */
    @Query(
            """
            SELECT new app.ageguessr.features.game.StartSeries.SeriesCandidate(
                ph.id, p.id, p.gender, p.continent)
            FROM app.ageguessr.features.people.PersonPhoto ph
            JOIN app.ageguessr.features.people.Person p ON p.id = ph.personId
            WHERE ph.reviewStatus = app.ageguessr.features.people.ReviewStatus.APPROVED
              AND p.excluded = false
            """)
    List<SeriesCandidate> findCandidates();

    /** The photos of the daily game of a day: never in a series that day, or it would spoil it. */
    @Query(
            """
            SELECT r.photoId FROM app.ageguessr.features.game.DailyRound r
            WHERE r.gameDate = :day AND r.photoId IS NOT NULL
            """)
    Set<UUID> findDailyPhotos(@Param("day") LocalDate day);

    /**
     * The photos of the daily games a player has started. JPQL rather than SQL: the
     * player's other id is null, and a typed parameter compares it to nothing.
     */
    @Query(
            """
            SELECT r.photoId FROM app.ageguessr.features.game.DailyRound r
            JOIN app.ageguessr.features.game.GamePlay g ON g.gameDate = r.gameDate AND g.position = r.position
            WHERE r.photoId IS NOT NULL AND (g.userId = :userId OR g.guestId = :guestId)
            """)
    Set<UUID> findDailySeenBy(@Param("userId") UUID userId, @Param("guestId") UUID guestId);

    /** The photos of the series a player has created or joined. */
    @Query(
            """
            SELECT r.photoId FROM app.ageguessr.features.game.FreeSeriesRound r
            JOIN app.ageguessr.features.game.FreeSeriesPlayer p ON p.seriesId = r.seriesId
            WHERE r.photoId IS NOT NULL AND (p.userId = :userId OR p.guestId = :guestId)
            """)
    Set<UUID> findSeriesSeenBy(@Param("userId") UUID userId, @Param("guestId") UUID guestId);

    default Set<UUID> findSeenBy(PlayerKey player) {
        Set<UUID> seen = new HashSet<>(findDailySeenBy(player.userId(), player.guestId()));
        seen.addAll(findSeriesSeenBy(player.userId(), player.guestId()));
        return seen;
    }
}
