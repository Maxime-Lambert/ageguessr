package app.ageguessr.features.friends.GetFriendsToday;

import java.time.LocalDate;
import java.util.List;

/**
 * Today's scores of the caller and their friends. Points only: never a guess, a name or an
 * age. Empty and locked until the caller's own game is complete.
 */
public record FriendsTodayResponse(LocalDate date, boolean locked, List<Player> players) {

    /** @param points null unless COMPLETE, as max and rounds */
    public record Player(String username, boolean me, String status, Integer points, Integer max, List<Round> rounds) {}

    public record Round(short position, int points) {}
}
