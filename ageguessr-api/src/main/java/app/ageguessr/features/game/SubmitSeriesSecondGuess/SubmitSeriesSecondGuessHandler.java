package app.ageguessr.features.game.SubmitSeriesSecondGuess;

import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlay;
import app.ageguessr.features.game.FreeSeriesPlayRepository;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import app.ageguessr.features.game.SeriesViews;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ends a round of a series and reveals it, as the daily game does (SubmitSecondGuessHandler). */
@Service
@RequiredArgsConstructor
public class SubmitSeriesSecondGuessHandler {

    private final SeriesViews series;
    private final GameRounds rounds;
    private final FreeSeriesPlayRepository plays;
    private final RoundViews views;
    private final Clock clock;

    @Transactional
    public RoundView handle(PlayerKey player, UUID seriesId, short position, SubmitSeriesSecondGuessCommand command) {
        FreeSeries found = series.ofPlayer(seriesId, player);
        GameRound round = rounds.playable(found, position);
        FreeSeriesPlay play = plays.findOf(player, seriesId, position).orElseThrow(GameErrors::wrongStep);
        boolean anyCategoryAvailable =
                play.step() == RoundStep.AWAITING_HINT && views.anyCategoryAvailable(round, play);
        if (!play.acceptsSecondGuess(anyCategoryAvailable)) {
            throw GameErrors.wrongStep();
        }
        play.finishWithSecondGuess(command.guess(), round.age(), clock.instant());
        try {
            plays.saveAndFlush(play);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw GameErrors.wrongStep();
        }
        return views.of(round, play);
    }
}
