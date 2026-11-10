package app.ageguessr.features.friends.GetFriendsLeaderboard;

import java.util.List;

/** The caller and their friends, ranked by average points per game. */
public record FriendsLeaderboardResponse(List<Player> players) {

    /** @param rank null for a player with no game yet, as averagePoints */
    public record Player(
            Integer rank, String username, boolean me, int gamesPlayed, Integer averagePoints, int currentStreak) {}
}
