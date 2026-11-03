package app.ageguessr.features.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FreeSeriesPlayerRepository extends JpaRepository<FreeSeriesPlayer, UUID> {

    List<FreeSeriesPlayer> findBySeriesId(UUID seriesId);

    Optional<FreeSeriesPlayer> findBySeriesIdAndUserId(UUID seriesId, UUID userId);

    Optional<FreeSeriesPlayer> findBySeriesIdAndGuestId(UUID seriesId, UUID guestId);

    default Optional<FreeSeriesPlayer> findOf(UUID seriesId, PlayerKey player) {
        return player.userId() != null
                ? findBySeriesIdAndUserId(seriesId, player.userId())
                : findBySeriesIdAndGuestId(seriesId, player.guestId());
    }

    /** The name a player shows in a challenge: the username of an account, else the guest's nickname. */
    interface PlayerName {
        UUID getUserId();

        UUID getGuestId();

        String getName();
    }

    /**
     * SQL rather than JPQL: the username belongs to the auth feature, which the game never
     * imports (ArchitectureTests). Read at each display, so a renamed account shows its new
     * name, and a deleted one is gone with its row (V15 cascade).
     */
    @Query(
            value =
                    """
                    SELECT p.user_id AS userId, p.guest_id AS guestId, COALESCE(u.username, p.nickname) AS name
                    FROM free_series_players p
                    LEFT JOIN users u ON u.id = p.user_id
                    WHERE p.series_id = :seriesId
                    """,
            nativeQuery = true)
    List<PlayerName> findNamesOf(@Param("seriesId") UUID seriesId);

    /**
     * Whether a nickname is an account's username, whatever its case: a guest may not take
     * it, or they would pass for that account in a leaderboard. SQL, as findNamesOf.
     */
    @Query(
            value = "SELECT EXISTS (SELECT 1 FROM users WHERE LOWER(username) = LOWER(:nickname))",
            nativeQuery = true)
    boolean isAccountUsername(@Param("nickname") String nickname);
}
