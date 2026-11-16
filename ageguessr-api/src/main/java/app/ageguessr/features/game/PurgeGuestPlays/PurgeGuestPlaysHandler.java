package app.ageguessr.features.game.PurgeGuestPlays;

import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GuestProperties;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the guest rounds played longer ago than the retention (7 days), whatever their
 * day: a past day caught up from the calendar today is kept 7 days too. A guest id is pseudonymous, but it is still personal data to
 * keep no longer than it serves. An account's games go with the account (V13 cascade).
 */
@Service
@RequiredArgsConstructor
public class PurgeGuestPlaysHandler {

    private final GamePlayRepository plays;
    private final GuestProperties properties;
    private final Clock clock;

    /** @return the number of rows deleted */
    @Transactional
    public int purge() {
        return plays.deleteGuestPlaysStartedBefore(
                clock.instant().minus(Duration.ofDays(properties.retentionDays())));
    }
}
