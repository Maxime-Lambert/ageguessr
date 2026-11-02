package app.ageguessr.features.game;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GamePlayRepository extends JpaRepository<GamePlay, UUID> {

    List<GamePlay> findByUserIdAndGameDate(UUID userId, LocalDate gameDate);

    List<GamePlay> findByGuestIdAndGameDate(UUID guestId, LocalDate gameDate);

    Optional<GamePlay> findByUserIdAndGameDateAndPosition(UUID userId, LocalDate gameDate, short position);

    Optional<GamePlay> findByGuestIdAndGameDateAndPosition(UUID guestId, LocalDate gameDate, short position);

    /** The rounds a player has started on a day, whether an account or a guest. */
    default List<GamePlay> findOf(PlayerKey player, LocalDate gameDate) {
        return player.userId() != null
                ? findByUserIdAndGameDate(player.userId(), gameDate)
                : findByGuestIdAndGameDate(player.guestId(), gameDate);
    }

    default Optional<GamePlay> findOf(PlayerKey player, LocalDate gameDate, short position) {
        return player.userId() != null
                ? findByUserIdAndGameDateAndPosition(player.userId(), gameDate, position)
                : findByGuestIdAndGameDateAndPosition(player.guestId(), gameDate, position);
    }

    /** Guest rows only: an account's games are kept as long as the account. */
    @Modifying
    @Query("DELETE FROM GamePlay g WHERE g.guestId IS NOT NULL AND g.gameDate < :before")
    int deleteGuestPlaysBefore(@Param("before") LocalDate before);
}
