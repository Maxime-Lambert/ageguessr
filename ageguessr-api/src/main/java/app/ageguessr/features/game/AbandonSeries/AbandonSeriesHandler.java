package app.ageguessr.features.game.AbandonSeries;

import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesRepository;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.SeriesViews;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A free game given up by its creator (testers, 2026-11-23): it was picked up again on
 * every visit (GetCurrentSeries) with no way out. Deleted rather than marked: unfinished,
 * it was never shared (a series is shared once played), so no challenge points to it,
 * and the retention would delete it after 7 days anyway. Its rounds, players and plays go
 * with it (V15 cascades). A finished one stays: it may be shared, and there is nothing
 * left to give up.
 */
@Service
@RequiredArgsConstructor
public class AbandonSeriesHandler {

    private final SeriesViews views;
    private final FreeSeriesRepository series;

    @Transactional
    public void handle(PlayerKey player, UUID seriesId) {
        FreeSeries found = views.ofPlayer(seriesId, player);
        if (!found.isCreatedBy(player)) {
            throw GameErrors.notCreator();
        }
        if (found.shared() || views.of(found, player).score().complete()) {
            throw GameErrors.seriesFinished();
        }
        series.delete(found);
    }
}
