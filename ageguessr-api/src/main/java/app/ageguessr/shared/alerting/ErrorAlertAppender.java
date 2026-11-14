package app.ageguessr.shared.alerting;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;

/**
 * Hands every ERROR log line to {@link ErrorAlerts}. The logs of the email and alerting
 * code are left out: an alert that cannot be sent must not raise another alert.
 */
class ErrorAlertAppender extends AppenderBase<ILoggingEvent> {

    private static final String[] IGNORED = {"app.ageguessr.shared.email.", "app.ageguessr.shared.alerting."};

    private final ErrorAlerts alerts;

    ErrorAlertAppender(ErrorAlerts alerts) {
        this.alerts = alerts;
        setName("error-alerts");
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.ERROR) || ignored(event.getLoggerName())) {
            return;
        }
        IThrowableProxy thrown = event.getThrowableProxy();
        alerts.report(new LoggedError(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLoggerName(),
                event.getFormattedMessage(),
                thrown == null ? null : thrown.getClassName()));
    }

    private static boolean ignored(String logger) {
        for (String prefix : IGNORED) {
            if (logger.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
