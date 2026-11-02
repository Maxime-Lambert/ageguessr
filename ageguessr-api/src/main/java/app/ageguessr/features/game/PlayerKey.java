package app.ageguessr.features.game;

import java.util.UUID;

/**
 * Who plays: an account when the request carries a valid JWT, otherwise the random id of
 * the guest cookie. Exactly one of the two, as V13 requires. The account wins when both
 * are there; guest games are not merged into the account.
 */
public record PlayerKey(UUID userId, UUID guestId) {

    public PlayerKey {
        if ((userId == null) == (guestId == null)) {
            throw new IllegalArgumentException("A player is either an account or a guest");
        }
    }

    public static PlayerKey account(UUID userId) {
        return new PlayerKey(userId, null);
    }

    public static PlayerKey guest(UUID guestId) {
        return new PlayerKey(null, guestId);
    }
}
