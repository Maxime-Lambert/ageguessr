package app.ageguessr.shared.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.shared.email.EmailMessage;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ErrorAlertAppenderTest {

    private final List<EmailMessage> sent = new ArrayList<>();
    private final LoggerContext context = new LoggerContext();
    private ErrorAlertAppender appender;

    @BeforeEach
    void plug() {
        appender = new ErrorAlertAppender(new ErrorAlerts(sent::add, "admin@x.test", Clock.systemUTC(), Runnable::run));
        appender.setContext(context);
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @AfterEach
    void unplug() {
        appender.stop();
        context.stop();
    }

    @Test
    void passesOnErrorsWithTheirExceptionClass() {
        context.getLogger("app.ageguessr.features.game.Foo").error("draw failed for {}", "today",
                new IllegalStateException("no photo"));

        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().htmlBody())
                .contains("app.ageguessr.features.game.Foo — java.lang.IllegalStateException: draw failed for today");
    }

    @Test
    void ignoresWarningsAndTheEmailAndAlertingCode() {
        context.getLogger("app.ageguessr.features.game.Foo").warn("only a warning");
        context.getLogger("app.ageguessr.shared.email.ResendEmailSender").error("Resend down");
        context.getLogger("app.ageguessr.shared.alerting.ErrorAlerts").error("could not send");

        assertThat(sent).isEmpty();
    }
}
