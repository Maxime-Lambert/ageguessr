package app.ageguessr.features.friends.AcceptFriendRequest;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.FriendLimits;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only the addressee accepts. Anyone else, or a request cancelled meanwhile: 404. */
@Service
@RequiredArgsConstructor
public class AcceptFriendRequestHandler {

    private final FriendshipRepository friendships;
    private final FriendLimits limits;
    private final Clock clock;

    @Transactional
    public void handle(UUID userId, UUID requestId) {
        Friendship request = friendships
                .findById(requestId)
                .filter(found -> !found.isAccepted() && found.getAddresseeId().equals(userId))
                .orElseThrow(FriendErrors::noSuchRequest);
        limits.checkCanBecomeFriends(userId, request.getRequesterId());
        request.accept(Instant.now(clock));
    }
}
