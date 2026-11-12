package app.ageguessr.features.game.RevealHint;

import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import app.ageguessr.features.hints.Hint;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gives the hints of the categories chosen, once per round: per category, the same for every player. */
@Service
@RequiredArgsConstructor
public class RevealHintHandler {

    private final GameRounds rounds;
    private final GamePlayRepository plays;
    private final RoundViews views;

    @Transactional
    public RoundView handle(PlayerKey player, LocalDate gameDate, short position, RevealHintCommand command) {
        GameRound round = rounds.playable(gameDate, position);
        GamePlay play = plays.findOf(player, gameDate, position)
                .filter(found -> found.step() == RoundStep.AWAITING_HINT)
                .orElseThrow(GameErrors::wrongStep);
        List<Hint> hints = views.offer(round, play.getLanguage())
                .pickAll(round.hintKey(), command.categories(), play.getLanguage(), views.hintsPerRound());
        play.revealHints(hints.stream().map(Hint::getCategory).toList(), hints.stream().map(Hint::getId).toList());
        try {
            plays.saveAndFlush(play);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Another tab chose a hint first.
            throw GameErrors.wrongStep();
        }
        return views.of(round, play);
    }
}
