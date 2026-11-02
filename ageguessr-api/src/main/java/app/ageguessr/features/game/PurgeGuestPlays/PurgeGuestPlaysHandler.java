package app.ageguessr.features.game.PurgeGuestPlays;

import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GuestProperties;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the guest games older than the retention: with 7 days, today's game and the
 * 7 days before are kept. A guest id is pseudonymous, but it is still personal data to
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
        return plays.deleteGuestPlaysBefore(LocalDate.now(clock).minusDays(properties.retentionDays()));
    }
}
