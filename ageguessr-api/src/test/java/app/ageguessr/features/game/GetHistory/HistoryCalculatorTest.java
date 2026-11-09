package app.ageguessr.features.game.GetHistory;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.game.GetHistory.HistoryCalculator.Day;
import app.ageguessr.features.game.GetHistory.HistoryCalculator.Play;
import app.ageguessr.features.game.GetHistory.HistoryCalculator.Result;
import app.ageguessr.features.game.GetHistory.HistoryCalculator.Stats;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private final List<Play> plays = new ArrayList<>();
    private final Map<LocalDate, Map<Short, Boolean>> playable = new HashMap<>();

    @Test
    void noGameGivesEmptyDaysAndZeroStats() {
        Result result = compute();

        assertThat(result.days()).isEmpty();
        assertThat(result.stats()).isEqualTo(new Stats(0, 0, 0, 0, 0));
    }

    @Test
    void aFinishedDaySumsItsRoundsOutOfThreeThousand() {
        fullDay(TODAY, 600, 1000, 400);

        Day day = compute().days().getFirst();

        assertThat(day).isEqualTo(new Day(TODAY, 2000, 3000, true));
    }

    @Test
    void aRoundStartedButNotFinishedCountsNoPointsAndLeavesTheDayIncomplete() {
        threeRounds(TODAY);
        plays.add(new Play(TODAY, (short) 1, true, 700));
        plays.add(new Play(TODAY, (short) 2, false, 0));

        Day day = compute().days().getFirst();

        assertThat(day).isEqualTo(new Day(TODAY, 700, 3000, false));
    }

    @Test
    void aRoundNeutralizedAfterItWasPlayedKeepsItsPointsAndItsPlaceInTheMaximum() {
        fullDay(TODAY, 600, 1000, 400);
        playable.get(TODAY).put((short) 2, false);

        assertThat(compute().days().getFirst()).isEqualTo(new Day(TODAY, 2000, 3000, true));
    }

    @Test
    void aRoundNeutralizedBeforeItWasFinishedLeavesTheMaximumAndDoesNotHoldTheDayBack() {
        threeRounds(TODAY);
        playable.get(TODAY).put((short) 3, false);
        plays.add(new Play(TODAY, (short) 1, true, 500));
        plays.add(new Play(TODAY, (short) 2, true, 300));
        plays.add(new Play(TODAY, (short) 3, false, 0));

        assertThat(compute().days().getFirst()).isEqualTo(new Day(TODAY, 800, 2000, true));
    }

    @Test
    void listsTheDaysMostRecentFirst() {
        fullDay(TODAY.minusDays(5), 100, 100, 100);
        fullDay(TODAY, 100, 100, 100);
        fullDay(TODAY.minusDays(1), 100, 100, 100);

        assertThat(compute().days())
                .extracting(Day::date)
                .containsExactly(TODAY, TODAY.minusDays(1), TODAY.minusDays(5));
    }

    @Test
    void theCurrentStreakIncludesTodayOnceFinished() {
        fullDay(TODAY, 100, 100, 100);
        fullDay(TODAY.minusDays(1), 100, 100, 100);
        fullDay(TODAY.minusDays(2), 100, 100, 100);

        assertThat(compute().stats().currentStreak()).isEqualTo(3);
    }

    @Test
    void todayNotFinishedYetDoesNotBreakTheStreak() {
        fullDay(TODAY.minusDays(1), 100, 100, 100);
        fullDay(TODAY.minusDays(2), 100, 100, 100);
        threeRounds(TODAY);
        plays.add(new Play(TODAY, (short) 1, true, 100));

        assertThat(compute().stats().currentStreak()).isEqualTo(2);
    }

    @Test
    void aMissedDayEndsTheStreak() {
        fullDay(TODAY, 100, 100, 100);
        fullDay(TODAY.minusDays(2), 100, 100, 100);
        fullDay(TODAY.minusDays(3), 100, 100, 100);

        Stats stats = compute().stats();

        assertThat(stats.currentStreak()).isEqualTo(1);
        assertThat(stats.bestStreak()).isEqualTo(2);
    }

    @Test
    void noGameYesterdayOrTodayMeansNoCurrentStreak() {
        fullDay(TODAY.minusDays(2), 100, 100, 100);

        Stats stats = compute().stats();

        assertThat(stats.currentStreak()).isZero();
        assertThat(stats.bestStreak()).isEqualTo(1);
    }

    @Test
    void anIncompleteDayInTheMiddleBreaksTheStreak() {
        fullDay(TODAY, 100, 100, 100);
        threeRounds(TODAY.minusDays(1));
        plays.add(new Play(TODAY.minusDays(1), (short) 1, true, 100));
        fullDay(TODAY.minusDays(2), 100, 100, 100);

        assertThat(compute().stats().currentStreak()).isEqualTo(1);
    }

    @Test
    void theBestStreakCanBeAnOldOne() {
        for (int i = 10; i < 14; i++) {
            fullDay(TODAY.minusDays(i), 100, 100, 100);
        }
        fullDay(TODAY, 100, 100, 100);

        Stats stats = compute().stats();

        assertThat(stats.bestStreak()).isEqualTo(4);
        assertThat(stats.currentStreak()).isEqualTo(1);
    }

    @Test
    void aStreakCrossesTheNewYear() {
        LocalDate newYear = LocalDate.of(2027, 1, 1);
        fullDay(newYear, 100, 100, 100);
        fullDay(newYear.minusDays(1), 100, 100, 100);

        Result result = HistoryCalculator.compute(plays, playable, newYear);

        assertThat(result.stats().currentStreak()).isEqualTo(2);
        assertThat(result.stats().bestStreak()).isEqualTo(2);
    }

    @Test
    void theStatsCountCompleteDaysOnlyAndRoundTheAverage() {
        fullDay(TODAY.minusDays(1), 1000, 1000, 1000);
        fullDay(TODAY.minusDays(2), 500, 500, 500);
        fullDay(TODAY.minusDays(3), 0, 0, 1);
        threeRounds(TODAY);
        plays.add(new Play(TODAY, (short) 1, true, 1000));

        Stats stats = compute().stats();

        assertThat(stats.gamesPlayed()).isEqualTo(3);
        assertThat(stats.averagePoints()).isEqualTo(1500); // (3000 + 1500 + 1) / 3 = 1500.33
        assertThat(stats.bestPoints()).isEqualTo(3000);
    }

    @Test
    void theAverageRoundsHalfUp() {
        fullDay(TODAY.minusDays(1), 1, 0, 0);
        fullDay(TODAY.minusDays(2), 0, 0, 0);

        assertThat(compute().stats().averagePoints()).isEqualTo(1); // 0.5
    }

    @Test
    void aDayWhoseRoundsWereAllNeutralizedUnfinishedKeepsTheStreakButNotTheStats() {
        fullDay(TODAY.minusDays(1), 100, 100, 100);
        threeRounds(TODAY);
        playable.get(TODAY).replaceAll((position, value) -> false);
        plays.add(new Play(TODAY, (short) 1, false, 0));

        Result result = compute();

        assertThat(result.days().getFirst()).isEqualTo(new Day(TODAY, 0, 0, true));
        assertThat(result.stats().currentStreak()).isEqualTo(2);
        assertThat(result.stats().gamesPlayed()).isEqualTo(1);
    }

    private Result compute() {
        return HistoryCalculator.compute(plays, playable, TODAY);
    }

    private void threeRounds(LocalDate date) {
        playable.put(date, new HashMap<>(Map.of((short) 1, true, (short) 2, true, (short) 3, true)));
    }

    private void fullDay(LocalDate date, int first, int second, int third) {
        threeRounds(date);
        plays.add(new Play(date, (short) 1, true, first));
        plays.add(new Play(date, (short) 2, true, second));
        plays.add(new Play(date, (short) 3, true, third));
    }
}
