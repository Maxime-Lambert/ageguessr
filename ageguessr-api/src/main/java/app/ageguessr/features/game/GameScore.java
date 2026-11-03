package app.ageguessr.features.game;

import java.util.List;

/**
 * A player's score over rounds, daily or of a series: the finished playable rounds only,
 * a neutralized round being out of the score.
 *
 * @param max {@value Scoring#EXACT_FIRST_GUESS_ROUND} per playable round
 * @param complete no playable round is left to play
 */
public record GameScore(int points, int max, boolean complete) {

    public static GameScore of(List<RoundView> rounds) {
        List<RoundView> playable = rounds.stream()
                .filter(round -> round.status() == DailyRoundStatus.PLAYABLE)
                .toList();
        int points = playable.stream()
                .filter(round -> round.step() == RoundStep.FINISHED)
                .mapToInt(round -> round.result().points())
                .sum();
        boolean complete = playable.stream().allMatch(round -> round.step() == RoundStep.FINISHED);
        return new GameScore(points, playable.size() * Scoring.EXACT_FIRST_GUESS_ROUND, complete);
    }
}
