package app.ageguessr.features.game.DrawDailyGame;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Draws the game of the day at midnight UTC, and at startup in case the application was
 * down at midnight. Off when {@code app.game.daily.draw-enabled} is false (tests, one-off
 * commands). A failure is logged and never stops the application: the rest of the site
 * (accounts, curation) must keep working, and the next call draws the day anyway.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.game.daily.draw-enabled", havingValue = "true")
@RequiredArgsConstructor
class DailyDrawScheduler {

    private final DrawDailyGameHandler handler;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    void drawAtStartup() {
        drawToday();
    }

    // 5 seconds past midnight, a margin in case the timer fires a hair early: the date is
    // read from the clock, and must already be the new day.
    @Scheduled(cron = "5 0 0 * * *", zone = "UTC")
    void drawAtMidnight() {
        drawToday();
    }

    private void drawToday() {
        LocalDate today = LocalDate.now(clock);
        try {
            handler.drawIfMissing(today);
        } catch (RuntimeException e) {
            log.error("Could not draw the game of {}", today, e);
        }
    }
}
