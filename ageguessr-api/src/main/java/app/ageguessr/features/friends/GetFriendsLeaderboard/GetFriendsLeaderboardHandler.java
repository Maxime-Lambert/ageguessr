package app.ageguessr.features.friends.GetFriendsLeaderboard;

import app.ageguessr.features.friends.FriendGroup;
import app.ageguessr.features.game.PlayerHistories;
import app.ageguessr.features.game.PlayerHistory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computed at each read, no cache: 200 friends at most, a few thousand rounds in memory
 * (user decision, 2026-11-10).
 */
@Service
@RequiredArgsConstructor
public class GetFriendsLeaderboardHandler {

    private final FriendGroup friendGroup;
    private final PlayerHistories histories;

    @Transactional(readOnly = true)
    public FriendsLeaderboardResponse handle(UUID userId) {
        FriendGroup.Group group = friendGroup.of(userId);
        Map<UUID, PlayerHistories.Record> records = histories.of(group.members());
        List<LeaderboardRanking.Entry> entries = group.members().stream()
                .map(member -> {
                    PlayerHistory.Stats stats = records.get(member).result().stats();
                    return new LeaderboardRanking.Entry(
                            group.usernames().get(member),
                            member.equals(userId),
                            stats.gamesPlayed(),
                            stats.averagePoints(),
                            stats.currentStreak());
                })
                .toList();
        return new FriendsLeaderboardResponse(LeaderboardRanking.rank(entries).stream()
                .map(ranked -> new FriendsLeaderboardResponse.Player(
                        ranked.rank(),
                        ranked.entry().username(),
                        ranked.entry().me(),
                        ranked.entry().gamesPlayed(),
                        ranked.rank() == null ? null : ranked.entry().averagePoints(),
                        ranked.entry().currentStreak()))
                .toList());
    }
}
