package app.ageguessr.shared.alerting;

import java.time.Instant;

/** An error log line, as much of it as an alert email tells: no stack trace. */
record LoggedError(Instant time, String logger, String message, String exceptionClass) {}
