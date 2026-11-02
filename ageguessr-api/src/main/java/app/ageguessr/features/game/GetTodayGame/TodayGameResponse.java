package app.ageguessr.features.game.GetTodayGame;

import app.ageguessr.features.game.RoundView;
import java.time.LocalDate;
import java.util.List;

/**
 * Today's game for one player. Empty rounds when nothing could be drawn (empty pool).
 *
 * @param score the finished playable rounds only: a neutralized round is out of the score
 */
public record TodayGameResponse(LocalDate date, List<RoundView> rounds, Score score) {

    /**
     * @param max {@value app.ageguessr.features.game.Scoring#EXACT_FIRST_GUESS_ROUND} per playable round
     * @param complete no playable round is left to play
     */
    public record Score(int points, int max, boolean complete) {}
}
