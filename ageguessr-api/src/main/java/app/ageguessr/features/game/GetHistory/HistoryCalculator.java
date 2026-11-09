package app.ageguessr.features.game.GetHistory;

import app.ageguessr.features.game.Scoring;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an account's played rounds into days, statistics and streaks. Pure: no database,
 * no clock (today is given), so that every rule below is tested on its own.
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
 *   <li>A streak is a run of complete days in a row (UTC days, those of the draw). The
 *       current one runs up to today if today is complete, else up to yesterday: a game
 *       not finished yet does not break it.
 *   <li>The games played, average and best are over complete days with a maximum above 0.
 * </ul>
 */
final class HistoryCalculator {

    private HistoryCalculator() {}

    /** A round the account started: its points only count once finished. */
    record Play(LocalDate date, short position, boolean finished, int points) {}

    record Day(LocalDate date, int points, int max, boolean complete) {}

    record Stats(int gamesPlayed, int averagePoints, int bestPoints, int currentStreak, int bestStreak) {}

    record Result(List<Day> days, Stats stats) {}

    /**
     * @param plays the account's rounds, any order
     * @param playable for each day played, whether each of its rounds can be played now
     *     (its photo still approved), by position
     * @return the days, most recent first, and the statistics
     */
    static Result compute(List<Play> plays, Map<LocalDate, Map<Short, Boolean>> playable, LocalDate today) {
        List<Day> days = plays.stream()
                .map(Play::date)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(date -> day(date, plays, playable.getOrDefault(date, Map.of())))
                .toList();
        return new Result(days, stats(days, today));
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

    private static Stats stats(List<Day> days, LocalDate today) {
        Set<LocalDate> complete = new HashSet<>();
        List<Day> scored = new ArrayList<>();
        for (Day day : days) {
            if (day.complete()) {
                complete.add(day.date());
                if (day.max() > 0) {
                    scored.add(day);
                }
            }
        }
        int average = scored.isEmpty()
                ? 0
                : (int) Math.round(scored.stream().mapToInt(Day::points).average().orElse(0));
        int best = scored.stream().mapToInt(Day::points).max().orElse(0);
        return new Stats(scored.size(), average, best, currentStreak(complete, today), bestStreak(complete));
    }

    private static int currentStreak(Set<LocalDate> complete, LocalDate today) {
        LocalDate day = complete.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (complete.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    private static int bestStreak(Set<LocalDate> complete) {
        int best = 0;
        for (LocalDate day : complete) {
            // Only count from the first day of a run, so each run is walked once.
            if (complete.contains(day.minusDays(1))) {
                continue;
            }
            int length = 0;
            for (LocalDate next = day; complete.contains(next); next = next.plusDays(1)) {
                length++;
            }
            best = Math.max(best, length);
        }
        return best;
    }
}
