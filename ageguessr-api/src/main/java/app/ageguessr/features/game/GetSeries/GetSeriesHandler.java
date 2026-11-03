package app.ageguessr.features.game.GetSeries;

import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.SeriesResponse;
import app.ageguessr.features.game.SeriesViews;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A series, for one of its players only. */
@Service
@RequiredArgsConstructor
public class GetSeriesHandler {

    private final SeriesViews views;

    @Transactional(readOnly = true)
    public SeriesResponse handle(Optional<PlayerKey> player, UUID seriesId) {
        PlayerKey key = player.orElseThrow(GameErrors::seriesNotFound);
        return views.of(views.ofPlayer(seriesId, key), key);
    }
}
