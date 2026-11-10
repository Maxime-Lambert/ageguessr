package app.ageguessr.features.friends.GetFriendsToday;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.friends.GetFriendsToday.TodayStandings.Member;
import app.ageguessr.features.friends.GetFriendsToday.TodayStandings.RoundPoints;
import app.ageguessr.features.friends.GetFriendsToday.TodayStandings.Standing;
import app.ageguessr.features.friends.GetFriendsToday.TodayStandings.Status;
import app.ageguessr.features.game.PlayerHistory.Day;
import app.ageguessr.features.game.PlayerHistory.Play;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TodayStandingsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private static Member member(String username, boolean me, List<Play> plays, List<Day> days) {
        return new Member(username, me, plays, days);
    }

    private static Member complete(String username, boolean me, int... points) {
        List<Play> plays = new java.util.ArrayList<>();
        for (int i = 0; i < points.length; i++) {
            plays.add(new Play(TODAY, (short) (i + 1), true, points[i]));
        }
        int total = java.util.Arrays.stream(points).sum();
        return member(username, me, plays, List.of(new Day(TODAY, total, points.length * 1000, true)));
    }

    @Test
    void lockedUntilTheCallersDayIsComplete() {
        Member notPlayed = member("me", true, List.of(), List.of());
        Member started = member(
                "me", true, List.of(new Play(TODAY, (short) 1, true, 800)), List.of(new Day(TODAY, 800, 3000, false)));
        Member onlyYesterday = member("me", true, List.of(), List.of(new Day(YESTERDAY, 2000, 3000, true)));

        assertThat(TodayStandings.isUnlocked(notPlayed, TODAY)).isFalse();
        assertThat(TodayStandings.isUnlocked(started, TODAY)).isFalse();
        assertThat(TodayStandings.isUnlocked(onlyYesterday, TODAY)).isFalse();
        assertThat(TodayStandings.isUnlocked(complete("me", true, 500, 600, 700), TODAY)).isTrue();
    }

    @Test
    void showsPointsOnlyForACompleteDay() {
        Member inProgress = member(
                "watson",
                false,
                List.of(new Play(TODAY, (short) 1, true, 900)),
                List.of(new Day(TODAY, 900, 3000, false)));
        Member notPlayedToday = member("adler", false, List.of(), List.of(new Day(YESTERDAY, 2500, 3000, true)));

        List<Standing> standings =
                TodayStandings.rank(List.of(complete("me", true, 500, 600, 700), inProgress, notPlayedToday), TODAY);

        assertThat(standings.get(0))
                .isEqualTo(new Standing(
                        "me",
                        true,
                        Status.COMPLETE,
                        1800,
                        3000,
                        List.of(
                                new RoundPoints((short) 1, 500),
                                new RoundPoints((short) 2, 600),
                                new RoundPoints((short) 3, 700))));
        assertThat(standings.get(1)).isEqualTo(new Standing("watson", false, Status.IN_PROGRESS, null, null, null));
        assertThat(standings.get(2)).isEqualTo(new Standing("adler", false, Status.NOT_PLAYED, null, null, null));
    }

    @Test
    void ranksCompleteDaysByPointsThenUsername() {
        List<Standing> standings = TodayStandings.rank(
                List.of(
                        complete("me", true, 500, 500, 500),
                        complete("Zed", false, 1000, 1000, 1000),
                        complete("bob", false, 500, 500, 500),
                        complete("Amy", false, 500, 500, 500)),
                TODAY);

        assertThat(standings).extracting(Standing::username).containsExactly("Zed", "Amy", "bob", "me");
    }

    @Test
    void keepsOnlyTodaysFinishedRoundsInTheDetail() {
        Member neutralizedRound = member(
                "me",
                true,
                List.of(
                        new Play(YESTERDAY, (short) 1, true, 1000),
                        new Play(TODAY, (short) 2, true, 400),
                        new Play(TODAY, (short) 1, true, 300),
                        new Play(TODAY, (short) 3, false, 0)),
                List.of(new Day(TODAY, 700, 2000, true)));

        Standing standing = TodayStandings.rank(List.of(neutralizedRound), TODAY).getFirst();

        assertThat(standing.rounds())
                .containsExactly(new RoundPoints((short) 1, 300), new RoundPoints((short) 2, 400));
        assertThat(standing.max()).isEqualTo(2000);
    }
}
