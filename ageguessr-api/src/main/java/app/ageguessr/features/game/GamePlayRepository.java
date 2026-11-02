package app.ageguessr.features.game;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePlayRepository extends JpaRepository<GamePlay, UUID> {

    List<GamePlay> findByUserIdAndGameDate(UUID userId, LocalDate gameDate);

    List<GamePlay> findByGuestIdAndGameDate(UUID guestId, LocalDate gameDate);

    /** The rounds a player has started on a day, whether an account or a guest. */
    default List<GamePlay> findOf(PlayerKey player, LocalDate gameDate) {
        return player.userId() != null
                ? findByUserIdAndGameDate(player.userId(), gameDate)
                : findByGuestIdAndGameDate(player.guestId(), gameDate);
    }
}
