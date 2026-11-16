package app.ageguessr.features.game.GetCalendar;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.game.DayState;
import app.ageguessr.features.game.GetCalendar.CalendarDays.Day;
import app.ageguessr.features.game.PlayerHistory.Play;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarDaysTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private final Map<LocalDate, Map<Short, Boolean>> rounds = new HashMap<>();
    private final List<Play> plays = new ArrayList<>();

    @Test
    void listsEveryDayOfTheMonthInOrder() {
        List<Day> days = CalendarDays.of(YearMonth.of(2028, 2), LocalDate.of(2028, 3, 1), rounds, plays);

        assertThat(days).hasSize(29); // a leap year
        assertThat(days.getFirst().date()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(days.getLast().date()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void aDayNeverDrawnIsUnavailable() {
        assertThat(stateOf(TODAY.minusDays(3))).isEqualTo(DayState.UNAVAILABLE);
    }

    @Test
    void aDayToComeIsUnavailableEvenIfDrawn() {
        threeRounds(TODAY.plusDays(1));

        assertThat(stateOf(TODAY.plusDays(1))).isEqualTo(DayState.UNAVAILABLE);
    }

    @Test
    void aDrawnDayNotStartedIsAvailableTodayIncluded() {
        threeRounds(TODAY);
        threeRounds(TODAY.minusDays(10));

        assertThat(stateOf(TODAY)).isEqualTo(DayState.AVAILABLE);
        assertThat(stateOf(TODAY.minusDays(10))).isEqualTo(DayState.AVAILABLE);
    }

    @Test
    void aDayWithARoundLeftIsStarted() {
        LocalDate day = TODAY.minusDays(2);
        threeRounds(day);
        plays.add(new Play(day, (short) 1, true, 1000));
        plays.add(new Play(day, (short) 2, false, 0));

        assertThat(stateOf(day)).isEqualTo(DayState.STARTED);
    }

    @Test
    void aDayWithEveryRoundFinishedIsFinished() {
        LocalDate day = TODAY.minusDays(2);
        threeRounds(day);
        for (short position = 1; position <= 3; position++) {
            plays.add(new Play(day, position, true, 500));
        }

        assertThat(stateOf(day)).isEqualTo(DayState.FINISHED);
    }

    @Test
    void aRoundNeutralizedBeforeBeingPlayedNeverHoldsTheDayBack() {
        LocalDate day = TODAY.minusDays(2);
        threeRounds(day);
        rounds.get(day).put((short) 3, false);
        plays.add(new Play(day, (short) 1, true, 500));
        plays.add(new Play(day, (short) 2, true, 500));

        assertThat(stateOf(day)).isEqualTo(DayState.FINISHED);
    }

    @Test
    void aDayWhoseRoundsWereAllNeutralizedBeforeThePlayerCameIsUnavailable() {
        LocalDate day = TODAY.minusDays(2);
        threeRounds(day);
        rounds.get(day).replaceAll((position, playable) -> false);

        assertThat(stateOf(day)).isEqualTo(DayState.UNAVAILABLE);
    }

    @Test
    void thePlaysOfAnotherDayDoNotCount() {
        LocalDate day = TODAY.minusDays(2);
        threeRounds(day);
        plays.add(new Play(day.minusDays(1), (short) 1, true, 1000));

        assertThat(stateOf(day)).isEqualTo(DayState.AVAILABLE);
    }

    private DayState stateOf(LocalDate date) {
        return CalendarDays.of(NOVEMBER, TODAY, rounds, plays).stream()
                .filter(day -> day.date().equals(date))
                .findFirst()
                .orElseThrow()
                .state();
    }

    private void threeRounds(LocalDate date) {
        rounds.put(date, new HashMap<>(Map.of((short) 1, true, (short) 2, true, (short) 3, true)));
    }
}
