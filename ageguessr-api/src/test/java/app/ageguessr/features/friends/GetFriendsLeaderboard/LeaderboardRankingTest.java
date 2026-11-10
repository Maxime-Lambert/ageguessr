package app.ageguessr.features.friends.GetFriendsLeaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.friends.GetFriendsLeaderboard.LeaderboardRanking.Entry;
import app.ageguessr.features.friends.GetFriendsLeaderboard.LeaderboardRanking.Ranked;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaderboardRankingTest {

    @Test
    void ranksByAverageWithoutAMinimumOfGames() {
        List<Ranked> ranked = LeaderboardRanking.rank(List.of(
                new Entry("veteran", false, 120, 1900, 30),
                new Entry("newcomer", false, 1, 2800, 1),
                new Entry("me", true, 10, 2100, 2)));

        assertThat(ranked).extracting(r -> r.entry().username()).containsExactly("newcomer", "me", "veteran");
        assertThat(ranked).extracting(Ranked::rank).containsExactly(1, 2, 3);
    }

    @Test
    void moreGamesBreakATie() {
        List<Ranked> ranked = LeaderboardRanking.rank(
                List.of(new Entry("few", false, 2, 2000, 0), new Entry("many", false, 9, 2000, 0)));

        assertThat(ranked).extracting(r -> r.entry().username()).containsExactly("many", "few");
        assertThat(ranked).extracting(Ranked::rank).containsExactly(1, 2);
    }

    @Test
    void anExactTieSharesTheRankAndTheUsernameOnlyOrdersTheRows() {
        List<Ranked> ranked = LeaderboardRanking.rank(List.of(
                new Entry("zoe", false, 5, 2000, 0),
                new Entry("Adam", false, 5, 2000, 0),
                new Entry("last", false, 5, 1000, 0)));

        assertThat(ranked).extracting(r -> r.entry().username()).containsExactly("Adam", "zoe", "last");
        assertThat(ranked).extracting(Ranked::rank).containsExactly(1, 1, 3);
    }

    @Test
    void aPlayerWithNoGameComesLastWithoutARank() {
        List<Ranked> ranked = LeaderboardRanking.rank(List.of(
                new Entry("idle", false, 0, 0, 0), new Entry("low", false, 3, 100, 0), new Entry("me", true, 0, 0, 0)));

        assertThat(ranked).extracting(r -> r.entry().username()).containsExactly("low", "idle", "me");
        assertThat(ranked).extracting(Ranked::rank).containsExactly(1, null, null);
    }

    @Test
    void aloneIsFirst() {
        assertThat(LeaderboardRanking.rank(List.of(new Entry("me", true, 4, 1500, 4))))
                .containsExactly(new Ranked(1, new Entry("me", true, 4, 1500, 4)));
    }
}
