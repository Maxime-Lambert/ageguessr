package app.ageguessr.features.game.SubmitSeriesFirstGuess;

import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlay;
import app.ageguessr.features.game.FreeSeriesPlayRepository;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import app.ageguessr.features.game.SeriesViews;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts a round of a series, as the daily game does (SubmitFirstGuessHandler). */
@Service
@RequiredArgsConstructor
public class SubmitSeriesFirstGuessHandler {

    private final SeriesViews series;
    private final GameRounds rounds;
    private final FreeSeriesPlayRepository plays;
    private final RoundViews views;
    private final Clock clock;

    @Transactional
    public RoundView handle(PlayerKey player, UUID seriesId, short position, SubmitSeriesFirstGuessCommand command) {
        FreeSeries found = series.ofPlayer(seriesId, player);
        GameRound round = rounds.playable(found, position);
        if (plays.findOf(player, seriesId, position).isPresent()) {
            throw GameErrors.wrongStep();
        }
        FreeSeriesPlay play = FreeSeriesPlay.start(
                seriesId, position, player, command.language(), command.guess(), round.age(), clock.instant());
        try {
            plays.saveAndFlush(play);
        } catch (DataIntegrityViolationException e) {
            // The same first guess sent twice at once: the unique index of V15 lets one through.
            throw GameErrors.wrongStep();
        }
        return views.of(round, play);
    }
}
