package app.ageguessr.features.friends.RemoveFriend;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Either friend ends the friendship; the other is not told. */
@Service
@RequiredArgsConstructor
public class RemoveFriendHandler {

    private final FriendshipRepository friendships;

    @Transactional
    public void handle(UUID userId, UUID friendshipId) {
        Friendship friendship = friendships
                .findById(friendshipId)
                .filter(found -> found.isAccepted() && found.involves(userId))
                .orElseThrow(FriendErrors::noSuchFriend);
        friendships.delete(friendship);
    }
}
