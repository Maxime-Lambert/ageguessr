package app.ageguessr.shared.alerting;

import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;

/**
 * Hands every ERROR log line to {@link ErrorAlerts}. The logs of the email and alerting
 * code, and of the email provider's SDK, are left out: an alert that cannot be sent must
 * not raise another alert.
 */
class ErrorAlertAppender extends AppenderBase<ILoggingEvent> {

    private static final String[] IGNORED = {
        "app.ageguessr.shared.email.", "app.ageguessr.shared.alerting.", "com.resend."
    };

    private final ErrorAlerts alerts;

    ErrorAlertAppender(ErrorAlerts alerts) {
        this.alerts = alerts;
        setName("error-alerts");
        // Logback drops the lower levels before append(): the root logger sees them all.
        ThresholdFilter errorsOnly = new ThresholdFilter();
        errorsOnly.setLevel("ERROR");
        errorsOnly.start();
        addFilter(errorsOnly);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (ignored(event.getLoggerName())) {
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
