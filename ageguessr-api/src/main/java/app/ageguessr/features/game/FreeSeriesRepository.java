package app.ageguessr.features.game;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreeSeriesRepository extends JpaRepository<FreeSeries, UUID> {

    Optional<FreeSeries> findByShareCode(String shareCode);

    Optional<FreeSeries> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<FreeSeries> findFirstByGuestIdOrderByCreatedAtDesc(UUID guestId);

    /** The last series a player created, finished or not. */
    default Optional<FreeSeries> findLatestOf(PlayerKey player) {
        return player.userId() != null
                ? findFirstByUserIdOrderByCreatedAtDesc(player.userId())
                : findFirstByGuestIdOrderByCreatedAtDesc(player.guestId());
    }

    /**
     * The retention (user decision, 2026-11-03): a series never shared goes after 7 days,
     * a challenge 30 days after it was shared. Its rounds, players and plays go with it
     * (V15 cascades).
     */
    @Modifying
    @Query(
            """
            DELETE FROM FreeSeries s
            WHERE (s.sharedAt IS NULL AND s.createdAt < :unsharedBefore)
               OR s.sharedAt < :sharedBefore
            """)
    int deleteExpired(@Param("unsharedBefore") Instant unsharedBefore, @Param("sharedBefore") Instant sharedBefore);
}
