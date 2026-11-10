package app.ageguessr.features.friends.AcceptInvite;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.FriendInvite;
import app.ageguessr.features.friends.FriendInviteRepository;
import app.ageguessr.features.friends.FriendLimits;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.features.game.ShareCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opening someone's link and clicking « Add » makes the two friends at once: sharing the
 * link is its owner's consent, the click is the other's. A request already waiting
 * between them, in either direction, is accepted. Already friends: nothing to do.
 */
@Service
@RequiredArgsConstructor
public class AcceptInviteHandler {

    private final FriendInviteRepository invites;
    private final FriendshipRepository friendships;
    private final FriendLimits limits;
    private final Clock clock;

    @Transactional
    public void handle(UUID userId, String code) {
        FriendInvite invite =
                invites.findByCode(ShareCodes.normalize(code)).orElseThrow(FriendErrors::noSuchInvite);
        UUID owner = invite.getUserId();
        if (owner.equals(userId)) {
            throw FriendErrors.ownInvite();
        }
        Optional<Friendship> existing = friendships.findBetween(userId, owner);
        if (existing.map(Friendship::isAccepted).orElse(false)) {
            return;
        }
        limits.checkCanBecomeFriends(userId, owner);
        Instant now = Instant.now(clock);
        if (existing.isPresent()) {
            existing.get().accept(now);
            return;
        }
        try {
            friendships.saveAndFlush(Friendship.accepted(owner, userId, now));
        } catch (DataIntegrityViolationException e) {
            // A request between the two was sent at the same instant: try the link again.
            throw FriendErrors.requestAlreadySent();
        }
    }
}
