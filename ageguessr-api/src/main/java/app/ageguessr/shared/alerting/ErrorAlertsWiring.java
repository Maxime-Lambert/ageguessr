package app.ageguessr.shared.alerting;

import app.ageguessr.shared.email.EmailSender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Plugs {@link ErrorAlerts} into Logback once the application is ready (errors while
 * starting are seen by the deploy smoke test instead), and unplugs it when the context
 * closes. Off when {@code app.alerts.errors-enabled} is false (the tests) or without an
 * admin address (local runs).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.alerts.errors-enabled", havingValue = "true")
class ErrorAlertsWiring {

    // One daemon thread: alerts are rare, and a slow email provider never holds a request.
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "error-alerts");
        thread.setDaemon(true);
        return thread;
    });
    private final ErrorAlerts alerts;
    private final ErrorAlertAppender appender;

    ErrorAlertsWiring(EmailSender emailSender, @Value("${app.admin.email:}") String adminEmail, Clock clock) {
        this.alerts = adminEmail.isBlank() ? null : new ErrorAlerts(emailSender, adminEmail, clock, executor);
        this.appender = alerts == null ? null : new ErrorAlertAppender(alerts);
    }

    @EventListener(ApplicationReadyEvent.class)
    void plug() {
        if (appender == null) {
            log.info("No ADMIN_EMAIL: backend errors are not emailed");
            return;
        }
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender.setContext(context);
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @EventListener(ContextClosedEvent.class)
    void unplug() {
        if (appender != null) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            appender.stop();
        }
        // A few seconds for an alert being sent, then no more waiting on shutdown.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void flush() {
        if (alerts != null) {
            alerts.flush();
        }
    }
}
