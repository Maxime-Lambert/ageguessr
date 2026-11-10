package app.ageguessr.features.friends;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The caller and their friends, with their usernames: who the comparison and leaderboard show. */
@Component
@RequiredArgsConstructor
public class FriendGroup {

    private final FriendshipRepository friendships;
    private final FriendUsers users;

    /** @param members the caller first, then their friends */
    public record Group(List<UUID> members, Map<UUID, String> usernames) {}

    public Group of(UUID userId) {
        List<UUID> members = new ArrayList<>();
        members.add(userId);
        members.addAll(friendships.findFriendIdsOf(userId));
        Map<UUID, String> usernames = users.findUsernamesOf(members);
        // An account deleted between the two reads has no username left: it is not shown.
        members.removeIf(member -> !usernames.containsKey(member));
        return new Group(List.copyOf(members), Map.copyOf(usernames));
    }
}
