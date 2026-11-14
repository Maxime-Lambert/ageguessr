package app.ageguessr.shared.alerting;

import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.HtmlUtils;

/**
 * Emails the admin the errors logged by the backend (docs/decisions/architecture.md,
 * « Monitoring »). The first error goes out at once; the ones after it within
 * {@link #COOLDOWN} are counted and sent together when it ends, so that a burst (the
 * database down, every request failing) makes two emails, not hundreds.
 *
 * <p>Little in each line: the time, the logger, the exception class and the message cut
 * to {@link #MAX_MESSAGE} characters; no stack trace, no request. Enough to know where to
 * look in the logs, without copying player data into emails.
 *
 * <p>Thread-safe: errors come from any thread. The email is handed to {@code executor},
 * never sent on the thread that logged. A failure to send is logged at WARN by this class,
 * whose logs the appender ignores: no loop when the email provider is down.
 */
@Slf4j
public class ErrorAlerts {

    static final Duration COOLDOWN = Duration.ofMinutes(15);
    static final int MAX_LISTED = 5;
    static final int MAX_MESSAGE = 300;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final EmailSender emailSender;
    private final String adminEmail;
    private final Clock clock;
    private final Executor executor;

    private final Object lock = new Object();
    private Instant lastSent;
    private final List<String> pending = new ArrayList<>();
    private int pendingCount;

    public ErrorAlerts(EmailSender emailSender, String adminEmail, Clock clock, Executor executor) {
        this.emailSender = emailSender;
        this.adminEmail = adminEmail;
        this.clock = clock;
        this.executor = executor;
    }

    /** One error logged: sent now, or kept for the next summary while cooling down. */
    public void report(LoggedError error) {
        String line = line(error);
        synchronized (lock) {
            Instant now = clock.instant();
            if (pendingCount == 0 && (lastSent == null || !now.isBefore(lastSent.plus(COOLDOWN)))) {
                lastSent = now;
                send("[AgeGuessr] Backend error: " + oneLine(error.exceptionClass() != null
                        ? simpleName(error.exceptionClass())
                        : simpleName(error.logger())), List.of(line), 1);
                return;
            }
            pendingCount++;
            if (pending.size() < MAX_LISTED) {
                pending.add(line);
            }
        }
    }

    /** Sends the errors kept during the cooldown, once it has ended. Called every minute. */
    public void flush() {
        synchronized (lock) {
            if (pendingCount == 0 || clock.instant().isBefore(lastSent.plus(COOLDOWN))) {
                return;
            }
            lastSent = clock.instant();
            send("[AgeGuessr] " + pendingCount + " more backend error(s) in the last "
                    + COOLDOWN.toMinutes() + " min", List.copyOf(pending), pendingCount);
            pending.clear();
            pendingCount = 0;
        }
    }

    private void send(String subject, List<String> lines, int count) {
        StringBuilder body = new StringBuilder("<ul>");
        lines.forEach(line -> body.append("<li>").append(line).append("</li>"));
        body.append("</ul>");
        if (count > lines.size()) {
            body.append("<p>And ").append(count - lines.size()).append(" more.</p>");
        }
        body.append("<p>Details in the backend logs: <code>docker logs ageguessr-prod-backend-1</code></p>");
        EmailMessage message = new EmailMessage(adminEmail, subject, body.toString());
        executor.execute(() -> {
            try {
                emailSender.send(message);
            } catch (RuntimeException e) {
                log.warn("Could not send the error alert: {}", e.toString());
            }
        });
    }

    private static String line(LoggedError error) {
        String message = error.message() == null ? "" : error.message();
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE) + "…";
        }
        return HtmlUtils.htmlEscape(TIME.format(error.time()) + " " + error.logger()
                + (error.exceptionClass() != null ? " — " + error.exceptionClass() : "")
                + ": " + message, "UTF-8");
    }

    private static String simpleName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }

    /** A subject is one line, whatever an exception name holds. */
    private static String oneLine(String text) {
        return text.replaceAll("[\\r\\n]+", " ");
    }
}
