package app.ageguessr.features.game.GetCalendar;

import app.ageguessr.features.game.DayState;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * A month of the calendar for one player: never a photo, a name or a score, only states.
 *
 * @param firstMonth the month of the first day ever drawn, the furthest the calendar goes
 *     back; the current month before any draw
 */
public record CalendarResponse(YearMonth month, YearMonth firstMonth, List<Day> days) {

    public record Day(LocalDate date, DayState state) {}
}
