package app.ageguessr.features.auth.PurgeAccounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges once a day, after the purges of the game. Off when
 * {@code app.auth.retention.purge-enabled} is false (tests, one-off commands). A failure is
 * logged; the next day purges what is left.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.auth.retention.purge-enabled", havingValue = "true")
@RequiredArgsConstructor
class PurgeAccountsScheduler {

    private final PurgeAccountsHandler handler;

    @Scheduled(cron = "0 50 0 * * *", zone = "UTC")
    void purgeDaily() {
        try {
            PurgeAccountsHandler.Result result = handler.purge();
            log.info(
                    "Accounts purged: {} never verified, {} inactive; {} warned",
                    result.unverifiedDeleted(),
                    result.inactiveDeleted(),
                    result.warned());
        } catch (RuntimeException e) {
            log.error("Could not purge the accounts", e);
        }
    }
}
