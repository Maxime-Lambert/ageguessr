package app.ageguessr.shared.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.shared.email.EmailMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorAlertsTest {

    private static final Instant START = Instant.parse("2026-11-14T10:00:00Z");

    private final List<EmailMessage> sent = new ArrayList<>();
    private final MovingClock clock = new MovingClock(START);
    private final ErrorAlerts alerts = new ErrorAlerts(sent::add, "admin@x.test", clock, Runnable::run);

    private static LoggedError error(String message) {
        return new LoggedError(START, "app.ageguessr.features.game.DrawDailyGame.DrawDailyGameHandler",
                message, "java.lang.IllegalStateException");
    }

    @Test
    void sendsTheFirstErrorAtOnceToTheAdmin() {
        alerts.report(error("Game of 2026-11-14: only 0 of 3 rounds"));

        assertThat(sent).hasSize(1);
        EmailMessage email = sent.getFirst();
        assertThat(email.to()).isEqualTo("admin@x.test");
        assertThat(email.subject()).isEqualTo("[AgeGuessr] Backend error: IllegalStateException");
        assertThat(email.htmlBody())
                .contains("2026-11-14 10:00:00 UTC app.ageguessr.features.game.DrawDailyGame.DrawDailyGameHandler")
                .contains("java.lang.IllegalStateException: Game of 2026-11-14: only 0 of 3 rounds");
    }

    @Test
    void keepsTheErrorsOfTheCooldownForOneSummaryWhenItEnds() {
        alerts.report(error("first"));
        for (int i = 1; i <= 7; i++) {
            clock.advance(Duration.ofMinutes(1));
            alerts.report(error("burst " + i));
        }
        alerts.flush();
        assertThat(sent).hasSize(1);

        clock.advance(ErrorAlerts.COOLDOWN);
        alerts.flush();

        assertThat(sent).hasSize(2);
        EmailMessage summary = sent.get(1);
        assertThat(summary.subject()).isEqualTo("[AgeGuessr] 7 more backend error(s) in the last 15 min");
        assertThat(summary.htmlBody()).contains("burst 1", "burst 5").doesNotContain("burst 6").contains("And 2 more.");

        alerts.flush();
        assertThat(sent).hasSize(2);
    }

    @Test
    void sendsAtOnceAgainOnceACooldownWithoutErrorsHasPassed() {
        alerts.report(error("first"));
        clock.advance(ErrorAlerts.COOLDOWN);

        alerts.report(error("second"));

        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).htmlBody()).contains("second");
    }

    @Test
    void cutsLongMessagesAndEscapesThemAndKeepsTheSubjectOnOneLine() {
        alerts.report(new LoggedError(START, "some.Logger", "<b>" + "x".repeat(400), null));

        EmailMessage email = sent.getFirst();
        assertThat(email.subject()).isEqualTo("[AgeGuessr] Backend error: Logger");
        assertThat(email.htmlBody()).contains("&lt;b&gt;").doesNotContain("<b>").contains("…")
                .doesNotContain("x".repeat(ErrorAlerts.MAX_MESSAGE));
    }

    @Test
    void neverThrowsWhenTheEmailCannotBeSent() {
        ErrorAlerts failing = new ErrorAlerts(message -> {
            throw new IllegalStateException("Resend down");
        }, "admin@x.test", clock, Runnable::run);

        failing.report(error("boom"));
    }

    /** A clock the test moves forward. */
    private static final class MovingClock extends Clock {
        private Instant now;

        MovingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
