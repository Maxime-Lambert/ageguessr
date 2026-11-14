package app.ageguessr.functional.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * An error logged anywhere in the running application reaches the admin by email. Its
 * context is closed after the class: the appender sits on the JVM-wide Logback root, and
 * must not catch the errors other tests log on purpose.
 */
@SpringBootTest(properties = "app.alerts.errors-enabled=true")
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
class ErrorAlertsFunctionalTest {

    @Autowired
    private RecordingEmailSender emailSender;

    @Test
    void emailsAnErrorLoggedByTheBackendToTheAdmin() throws InterruptedException {
        LoggerFactory.getLogger("app.ageguessr.features.game.DrawDailyGame.DrawDailyGameHandler")
                .error("Game of 2026-11-14: only 0 of 3 rounds");

        EmailMessage email = awaitEmailTo("admin@ageguessr.test");
        assertThat(email.subject()).isEqualTo("[AgeGuessr] Backend error: DrawDailyGameHandler");
        assertThat(email.htmlBody()).contains("Game of 2026-11-14: only 0 of 3 rounds");
    }

    // Sent on the alerts' own thread: wait a little for it.
    private EmailMessage awaitEmailTo(String recipient) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            EmailMessage email = emailSender.lastMessageTo(recipient);
            if (email != null) {
                return email;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No alert email to " + recipient);
    }
}
