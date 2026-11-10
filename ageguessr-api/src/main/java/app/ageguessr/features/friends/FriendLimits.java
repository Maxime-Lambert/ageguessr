package app.ageguessr.features.friends;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The limits, checked by every use case that adds a friend or a request, written once.
 * Counted before the insert, so two requests at the same instant may go one over: a
 * bound against abuse, not an exact quota.
 */
@Component
@RequiredArgsConstructor
public class FriendLimits {

    private final FriendshipRepository friendships;
    private final FriendsProperties properties;

    /** Before a new friendship between the caller and another account. */
    public void checkCanBecomeFriends(UUID caller, UUID other) {
        if (friendships.countFriendsOf(caller) >= properties.maxFriends()) {
            throw FriendErrors.friendLimitReached();
        }
        if (friendships.countFriendsOf(other) >= properties.maxFriends()) {
            throw FriendErrors.otherFriendLimitReached();
        }
    }

    /** Before a new request: the sender's own limits only, the addressee decides later. */
    public void checkCanSendRequest(UUID sender) {
        if (friendships.countFriendsOf(sender) >= properties.maxFriends()) {
            throw FriendErrors.friendLimitReached();
        }
        if (friendships.countByRequesterIdAndStatus(sender, FriendshipStatus.PENDING)
                >= properties.maxPendingRequests()) {
            throw FriendErrors.pendingLimitReached();
        }
    }
}
