package app.ageguessr.features.game.PurgeSeries;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges once a day, after the guest purge. Off when {@code app.game.series.purge-enabled}
 * is false (tests, one-off commands). A failure is logged; the next day purges the rest.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.game.series.purge-enabled", havingValue = "true")
@RequiredArgsConstructor
class PurgeSeriesScheduler {

    private final PurgeSeriesHandler handler;

    @Scheduled(cron = "0 40 0 * * *", zone = "UTC")
    void purgeDaily() {
        try {
            log.info("Purged {} free series", handler.purge());
        } catch (RuntimeException e) {
            log.error("Could not purge the free series", e);
        }
    }
}
