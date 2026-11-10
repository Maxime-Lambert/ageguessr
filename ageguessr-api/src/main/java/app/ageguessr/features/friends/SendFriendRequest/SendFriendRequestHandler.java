package app.ageguessr.features.friends.SendFriendRequest;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.FriendLimits;
import app.ageguessr.features.friends.FriendUsers;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A request to the account a username names. A request already crossing the other way is
 * not turned into a friendship: accepting takes an explicit click (user decision,
 * 2026-11-10), the frontend points to it instead.
 */
@Service
@RequiredArgsConstructor
public class SendFriendRequestHandler {

    private final FriendshipRepository friendships;
    private final FriendUsers users;
    private final FriendLimits limits;
    private final Clock clock;

    @Transactional
    public void handle(UUID userId, SendFriendRequestCommand command) {
        UUID addressee = users.findVerifiedIdByUsername(command.username())
                .filter(id -> !id.equals(userId))
                .orElseThrow(FriendErrors::noSuchPlayer);
        friendships.findBetween(userId, addressee).ifPresent(existing -> {
            if (existing.isAccepted()) {
                throw FriendErrors.alreadyFriends();
            }
            throw existing.getRequesterId().equals(userId)
                    ? FriendErrors.requestAlreadySent()
                    : FriendErrors.incomingRequestExists();
        });
        limits.checkCanSendRequest(userId);
        try {
            friendships.saveAndFlush(Friendship.request(userId, addressee, Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Both sent at the same instant: the V18 pair index kept the other one.
            throw FriendErrors.requestAlreadySent();
        }
    }
}
