package app.ageguessr.functional.auth;

import app.ageguessr.shared.email.EmailMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the raw verification token from a recorded confirmation email's HTML body. */
final class VerificationLinks {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\"&]+)");

    private VerificationLinks() {}

    static String extractToken(EmailMessage message) {
        Matcher matcher = TOKEN_PATTERN.matcher(message.htmlBody());
        if (!matcher.find()) {
            throw new IllegalStateException("No verification token found in email body: " + message.htmlBody());
        }
        return matcher.group(1);
    }
}
