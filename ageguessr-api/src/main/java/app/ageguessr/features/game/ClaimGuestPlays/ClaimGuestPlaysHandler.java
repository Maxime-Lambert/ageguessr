package app.ageguessr.features.game.ClaimGuestPlays;

import app.ageguessr.features.game.GamePlayRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives an account the daily games its player played as a guest on this browser, so that
 * logging in keeps today's game and the streak. A round the account already played wins
 * over the guest's. Running it again finds nothing left: it is safe to call on every
 * login, and two logins at once cannot both move the same rows.
 */
@Service
@RequiredArgsConstructor
public class ClaimGuestPlaysHandler {

    private final GamePlayRepository plays;

    /** @return the number of rounds moved to the account */
    @Transactional
    public int handle(UUID guestId, UUID userId) {
        plays.deleteGuestPlaysAlreadyPlayedBy(guestId, userId);
        return plays.moveGuestPlaysTo(guestId, userId);
    }
}
