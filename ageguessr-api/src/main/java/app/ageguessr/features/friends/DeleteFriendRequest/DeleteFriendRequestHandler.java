package app.ageguessr.features.friends.DeleteFriendRequest;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Declined by its addressee or cancelled by its sender: the row goes, without a trace. The
 * sender is not told, the request just leaves their list, and they may send a new one
 * (user decision, 2026-11-10).
 */
@Service
@RequiredArgsConstructor
public class DeleteFriendRequestHandler {

    private final FriendshipRepository friendships;

    @Transactional
    public void handle(UUID userId, UUID requestId) {
        Friendship request = friendships
                .findById(requestId)
                .filter(found -> !found.isAccepted() && found.involves(userId))
                .orElseThrow(FriendErrors::noSuchRequest);
        friendships.delete(request);
    }
}
