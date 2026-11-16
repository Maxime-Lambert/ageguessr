package app.ageguessr.features.game;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an account's played rounds into days and statistics. Pure: no database, no
 * clock, so that every rule below is tested on its own. Public: the
 * profile (GetHistory) and the friends' comparison and leaderboard share these rules,
 * written once (docs/decisions/architecture.md, the friends -> game exception).
 *
 * <p>The past is not rewritten, as for the points stored at each guess (V13): a round
 * neutralized after it was finished keeps its points and stays in the day's maximum. A
 * round neutralized before it was finished leaves the maximum, as it does on the day.
 *
 * <ul>
 *   <li>A round counts in the maximum when the player finished it, or when it can still
 *       be played.
 *   <li>A day is complete when every round still playable is finished: a neutralized
 *       round never holds a day back.
 * *   <li>The games played, average and best are over complete days with a maximum above 0,
 *       whenever they were played: a past day caught up from the calendar counts as any
 *       other (no streaks, user decision 2026-11-16).
 * </ul>
 */
public final class PlayerHistory {

    private PlayerHistory() {}

    /** A round the account started: its points only count once finished. */
    public record Play(LocalDate date, short position, boolean finished, int points) {}

    public record Day(LocalDate date, int points, int max, boolean complete) {}

    public record Stats(int gamesPlayed, int averagePoints, int bestPoints) {}

    public record Result(List<Day> days, Stats stats) {}

    /**
     * @param plays the account's rounds, any order
     * @param playable for each day played, whether each of its rounds can be played now
     *     (its photo still approved), by position
     * @return the days, most recent first, and the statistics
     */
    public static Result compute(List<Play> plays, Map<LocalDate, Map<Short, Boolean>> playable) {
        List<Day> days = plays.stream()
                .map(Play::date)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(date -> day(date, plays, playable.getOrDefault(date, Map.of())))
                .toList();
        return new Result(days, stats(days));
    }

    private static Day day(LocalDate date, List<Play> plays, Map<Short, Boolean> rounds) {
        List<Play> ofDay = plays.stream().filter(play -> play.date().equals(date)).toList();
        Set<Short> positions = new HashSet<>(rounds.keySet());
        ofDay.forEach(play -> positions.add(play.position()));
        int points = 0;
        int counted = 0;
        boolean complete = true;
        for (short position : positions) {
            boolean stillPlayable = rounds.getOrDefault(position, false);
            boolean finished = ofDay.stream().anyMatch(play -> play.position() == position && play.finished());
            if (finished) {
                points += ofDay.stream()
                        .filter(play -> play.position() == position)
                        .mapToInt(Play::points)
                        .sum();
            }
            if (finished || stillPlayable) {
                counted++;
            }
            if (stillPlayable && !finished) {
                complete = false;
            }
        }
        return new Day(date, points, counted * Scoring.EXACT_FIRST_GUESS_ROUND, complete);
    }

    private static Stats stats(List<Day> days) {
        List<Day> scored = new ArrayList<>();
        for (Day day : days) {
            if (day.complete() && day.max() > 0) {
                scored.add(day);
            }
        }
        int average = scored.isEmpty()
                ? 0
                : (int) Math.round(scored.stream().mapToInt(Day::points).average().orElse(0));
        int best = scored.stream().mapToInt(Day::points).max().orElse(0);
        return new Stats(scored.size(), average, best);
    }
}
