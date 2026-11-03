package app.ageguessr.features.game.RevealSeriesHint;

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
import app.ageguessr.features.hints.Hint;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives the hint of the category chosen, the same for every player of the series: the
 * key of the round holds the series, so friends comparing scores had the same help.
 */
@Service
@RequiredArgsConstructor
public class RevealSeriesHintHandler {

    private final SeriesViews series;
    private final GameRounds rounds;
    private final FreeSeriesPlayRepository plays;
    private final RoundViews views;

    @Transactional
    public RoundView handle(PlayerKey player, UUID seriesId, short position, RevealSeriesHintCommand command) {
        FreeSeries found = series.ofPlayer(seriesId, player);
        GameRound round = rounds.playable(found, position);
        FreeSeriesPlay play = plays.findOf(player, seriesId, position)
                .filter(started -> started.step() == RoundStep.AWAITING_HINT)
                .orElseThrow(GameErrors::wrongStep);
        Hint hint = views.offer(round, play.getLanguage())
                .pick(round.hintKey(), command.category(), play.getLanguage())
                .orElseThrow(GameErrors::categoryUnavailable);
        play.revealHint(command.category(), hint.getId());
        try {
            plays.saveAndFlush(play);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw GameErrors.wrongStep();
        }
        return views.of(round, play);
    }
}
