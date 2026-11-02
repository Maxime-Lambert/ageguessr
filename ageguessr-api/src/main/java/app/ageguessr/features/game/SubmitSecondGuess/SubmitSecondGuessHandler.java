package app.ageguessr.features.game.SubmitSecondGuess;

import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ends a round and reveals it. The hint must have been taken, unless none could be offered. */
@Service
@RequiredArgsConstructor
public class SubmitSecondGuessHandler {

    private final GameRounds rounds;
    private final GamePlayRepository plays;
    private final RoundViews views;
    private final Clock clock;

    @Transactional
    public RoundView handle(PlayerKey player, LocalDate gameDate, short position, SubmitSecondGuessCommand command) {
        GameRound round = rounds.playable(gameDate, position);
        GamePlay play = plays.findOf(player, gameDate, position).orElseThrow(GameErrors::wrongStep);
        // The offer is read only while the hint is pending: it is what may let the player skip it.
        boolean anyCategoryAvailable =
                play.step() == RoundStep.AWAITING_HINT && views.anyCategoryAvailable(round, play);
        if (!play.acceptsSecondGuess(anyCategoryAvailable)) {
            throw GameErrors.wrongStep();
        }
        play.finishWithSecondGuess(command.guess(), round.age(), clock.instant());
        try {
            plays.saveAndFlush(play);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Another tab ended the round first.
            throw GameErrors.wrongStep();
        }
        return views.of(round, play);
    }
}
