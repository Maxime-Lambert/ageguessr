package app.ageguessr.features.game.PurgeGuestPlays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges once a day, after the draw. Off when {@code app.game.guest.purge-enabled} is
 * false (tests, one-off commands). A failure is logged; the next day purges the rows left.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.game.guest.purge-enabled", havingValue = "true")
@RequiredArgsConstructor
class PurgeGuestPlaysScheduler {

    private final PurgeGuestPlaysHandler handler;

    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    void purgeDaily() {
        try {
            log.info("Purged {} guest game rows", handler.purge());
        } catch (RuntimeException e) {
            log.error("Could not purge the guest games", e);
        }
    }
}
