package app.ageguessr.features.friends.GetFriendsLeaderboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The friends' leaderboard: plain average points per game, no minimum number of games
 * (user decision, 2026-11-10). A game and the average are the profile's (PlayerHistory).
 * Ties: more games first, then the same rank; the username only orders the rows. A player
 * with no game yet comes last, without a rank. Pure, tested on its own.
 */
final class LeaderboardRanking {

    private LeaderboardRanking() {}

    record Entry(String username, boolean me, int gamesPlayed, int averagePoints, int currentStreak) {}

    /** @param rank null for a player with no game yet; equal for equal average and games */
    record Ranked(Integer rank, Entry entry) {}

    private static final Comparator<Entry> ORDER = Comparator.comparing((Entry entry) -> entry.gamesPlayed() == 0)
            .thenComparing(Comparator.comparingInt(Entry::averagePoints).reversed())
            .thenComparing(Comparator.comparingInt(Entry::gamesPlayed).reversed())
            .thenComparing(entry -> entry.username().toLowerCase(Locale.ROOT));

    static List<Ranked> rank(List<Entry> entries) {
        List<Entry> sorted = entries.stream().sorted(ORDER).toList();
        List<Ranked> ranked = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Entry entry = sorted.get(i);
            if (entry.gamesPlayed() == 0) {
                ranked.add(new Ranked(null, entry));
                continue;
            }
            boolean tiedWithPrevious = i > 0 && tied(sorted.get(i - 1), entry);
            int rank = tiedWithPrevious ? ranked.get(i - 1).rank() : i + 1;
            ranked.add(new Ranked(rank, entry));
        }
        return ranked;
    }

    private static boolean tied(Entry a, Entry b) {
        return a.averagePoints() == b.averagePoints() && a.gamesPlayed() == b.gamesPlayed();
    }
}
