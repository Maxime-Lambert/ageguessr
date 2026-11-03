package app.ageguessr.features.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeSeriesPlayerRepository extends JpaRepository<FreeSeriesPlayer, UUID> {

    List<FreeSeriesPlayer> findBySeriesId(UUID seriesId);

    Optional<FreeSeriesPlayer> findBySeriesIdAndUserId(UUID seriesId, UUID userId);

    Optional<FreeSeriesPlayer> findBySeriesIdAndGuestId(UUID seriesId, UUID guestId);

    default Optional<FreeSeriesPlayer> findOf(UUID seriesId, PlayerKey player) {
        return player.userId() != null
                ? findBySeriesIdAndUserId(seriesId, player.userId())
                : findBySeriesIdAndGuestId(seriesId, player.guestId());
    }
}
