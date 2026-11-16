package app.ageguessr.features.game.GetCalendar;

import app.ageguessr.features.game.DayState;
import app.ageguessr.features.game.PlayerHistory;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The state of each day of a month for one player. Pure: no database, no clock (today is
 * given), so that every rule is tested on its own. A started day is told from a finished
 * one by PlayerHistory's rule of a complete day, not a copy of it.
 */
final class CalendarDays {

    private CalendarDays() {}

    record Day(LocalDate date, DayState state) {}

    /**
     * @param rounds for each day drawn in the month, whether each round can be played now,
     *     by position; a day missing was never drawn
     * @param plays the player's rounds of the month, any order
     * @return every day of the month, the first first
     */
    static List<Day> of(
            YearMonth month, LocalDate today, Map<LocalDate, Map<Short, Boolean>> rounds, List<PlayerHistory.Play> plays) {
        List<Day> days = new ArrayList<>();
        for (int dayOfMonth = 1; dayOfMonth <= month.lengthOfMonth(); dayOfMonth++) {
            LocalDate date = month.atDay(dayOfMonth);
            days.add(new Day(date, state(date, today, rounds.get(date), plays)));
        }
        return days;
    }

    private static DayState state(
            LocalDate date, LocalDate today, Map<Short, Boolean> rounds, List<PlayerHistory.Play> plays) {
        if (date.isAfter(today) || rounds == null) {
            return DayState.UNAVAILABLE;
        }
        boolean started = plays.stream().anyMatch(play -> play.date().equals(date));
        if (!started) {
            // Every round neutralized before the player came: nothing left to play.
            return rounds.containsValue(true) ? DayState.AVAILABLE : DayState.UNAVAILABLE;
        }
        return PlayerHistory.day(date, plays, rounds).complete() ? DayState.FINISHED : DayState.STARTED;
    }
}
