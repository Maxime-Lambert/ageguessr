package app.ageguessr.features.game.GetCurrentSeries;

import app.ageguessr.features.game.FreeSeriesRepository;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.SeriesResponse;
import app.ageguessr.features.game.SeriesViews;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The series a player left unfinished, to pick it up again: the last one they created,
 * if it still has a round to play. On the server rather than in the browser, so that an
 * account finds it on another device.
 */
@Service
@RequiredArgsConstructor
public class GetCurrentSeriesHandler {

    private final FreeSeriesRepository series;
    private final SeriesViews views;

    @Transactional(readOnly = true)
    public Optional<SeriesResponse> handle(Optional<PlayerKey> player) {
        return player.flatMap(key -> series.findLatestOf(key)
                .map(latest -> views.of(latest, key))
                .filter(view -> !view.score().complete()));
    }
}
