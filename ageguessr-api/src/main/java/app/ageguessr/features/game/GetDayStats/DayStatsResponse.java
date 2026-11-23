package app.ageguessr.features.game.GetDayStats;

import java.util.List;

/**
 * How the others did on a day, for a player who finished it.
 *
 * @param players who finished every round of the day, the asker included, guests too
 * @param betterThanPercent the share of the others whose total is below the asker's, null
 *     under {@link DayStats#MINIMUM_PLAYERS}: a few players make a share meaningless
 * @param rounds the first guess of the players, on average, per playable round; empty under
 *     the same minimum
 */
public record DayStatsResponse(int players, Integer betterThanPercent, List<RoundStats> rounds) {

    /** @param averageFirstGuess the mean of the first guesses, rounded: before any hint */
    public record RoundStats(short position, int averageFirstGuess) {}
}
