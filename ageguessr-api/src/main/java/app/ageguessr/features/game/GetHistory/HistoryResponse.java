package app.ageguessr.features.game.GetHistory;

import java.time.LocalDate;
import java.util.List;

/**
 * An account's statistics and one page of its daily games. Never a photo nor its public
 * id: an old image served by today's id would tie that id to a known answer
 * (docs/decisions/architecture.md, « Limites connues »).
 *
 * @param hasMore older days remain after this page
 */
public record HistoryResponse(Stats stats, List<Day> days, int page, boolean hasMore) {

    public record Stats(int gamesPlayed, int averagePoints, int bestPoints, int currentStreak, int bestStreak) {}

    /** @param max 1 000 per round that counted (see HistoryCalculator) */
    public record Day(LocalDate date, int points, int max, boolean complete, List<Round> rounds) {}

    /**
     * A round the account started.
     *
     * @param name in the round's language; null until the round is finished, or once its
     *     photo is no longer in the game ({@code neutralized}), as {@code ageAtPhoto}
     * @param points null until finished
     */
    public record Round(
            short position,
            boolean finished,
            boolean neutralized,
            String name,
            Integer ageAtPhoto,
            int firstGuess,
            Integer secondGuess,
            Integer points) {}
}
