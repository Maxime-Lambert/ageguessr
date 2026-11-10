package app.ageguessr.features.friends;

import app.ageguessr.features.game.ShareCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Creates and replaces invite codes, in the same alphabet as the challenge codes
 * (ShareCodes: 40 bits from SecureRandom, typed without confusion).
 */
@Component
@RequiredArgsConstructor
public class FriendInviteCodes {

    /** 40 bits: a collision is already unlikely, three in a row means a broken generator. */
    static final int MAX_TRIES = 3;

    private final FriendInviteRepository invites;
    private final ShareCodes codes;
    private final Clock clock;

    /** The account's invite, created on its first read. */
    public FriendInvite inviteOf(UUID userId) {
        return invites.findById(userId)
                .orElseGet(() -> invites.save(new FriendInvite(userId, freeCode(), Instant.now(clock))));
    }

    /** A new code: the old link stops working. */
    public FriendInvite replace(UUID userId) {
        FriendInvite invite = invites.findById(userId).orElse(null);
        if (invite == null) {
            return invites.save(new FriendInvite(userId, freeCode(), Instant.now(clock)));
        }
        invite.replace(freeCode(), Instant.now(clock));
        return invites.save(invite);
    }

    private String freeCode() {
        for (int i = 0; i < MAX_TRIES; i++) {
            String code = codes.next();
            if (!invites.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No free invite code after " + MAX_TRIES + " tries");
    }
}
