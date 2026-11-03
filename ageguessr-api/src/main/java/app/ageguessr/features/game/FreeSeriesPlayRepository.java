package app.ageguessr.features.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FreeSeriesPlayRepository extends JpaRepository<FreeSeriesPlay, UUID> {

    List<FreeSeriesPlay> findBySeriesId(UUID seriesId);

    List<FreeSeriesPlay> findBySeriesIdAndUserId(UUID seriesId, UUID userId);

    List<FreeSeriesPlay> findBySeriesIdAndGuestId(UUID seriesId, UUID guestId);

    Optional<FreeSeriesPlay> findBySeriesIdAndPositionAndUserId(UUID seriesId, short position, UUID userId);

    Optional<FreeSeriesPlay> findBySeriesIdAndPositionAndGuestId(UUID seriesId, short position, UUID guestId);

    /** The rounds of a series a player has started. */
    default List<FreeSeriesPlay> findOf(PlayerKey player, UUID seriesId) {
        return player.userId() != null
                ? findBySeriesIdAndUserId(seriesId, player.userId())
                : findBySeriesIdAndGuestId(seriesId, player.guestId());
    }

    default Optional<FreeSeriesPlay> findOf(PlayerKey player, UUID seriesId, short position) {
        return player.userId() != null
                ? findBySeriesIdAndPositionAndUserId(seriesId, position, player.userId())
                : findBySeriesIdAndPositionAndGuestId(seriesId, position, player.guestId());
    }
}
