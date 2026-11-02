package app.ageguessr.features.game.SubmitFirstGuess;

import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts a round. An exact guess ends it at 1 000; otherwise the player learns the side. */
@Service
@RequiredArgsConstructor
public class SubmitFirstGuessHandler {

    private final GameRounds rounds;
    private final GamePlayRepository plays;
    private final RoundViews views;
    private final Clock clock;

    @Transactional
    public RoundView handle(PlayerKey player, LocalDate gameDate, short position, SubmitFirstGuessCommand command) {
        GameRound round = rounds.playable(gameDate, position);
        if (plays.findOf(player, gameDate, position).isPresent()) {
            throw GameErrors.wrongStep();
        }
        GamePlay play = GamePlay.start(
                gameDate, position, player, command.language(), command.guess(), round.age(), clock.instant());
        try {
            plays.saveAndFlush(play);
        } catch (DataIntegrityViolationException e) {
            // The same first guess sent twice at once (double click, two tabs): the
            // unique index of V13 lets one through.
            throw GameErrors.wrongStep();
        }
        return views.of(round, play);
    }
}
