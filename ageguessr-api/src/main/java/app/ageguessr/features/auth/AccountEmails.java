package app.ageguessr.features.auth;

import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emails for sensitive account actions. Each one carries a fresh single-use link; the
 * bodies never include user-provided text (username, etc.), so nothing needs escaping.
 */
@Service
public class AccountEmails {

    private final AccountTokenService accountTokenService;
    private final EmailSender emailSender;
    private final String frontendUrl;
    private final Duration passwordResetTtl;
    private final Duration secureAccountTtl;
    private final Duration deletionTtl;

    public AccountEmails(
            AccountTokenService accountTokenService,
            EmailSender emailSender,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.account-token.password-reset-ttl-minutes}") long passwordResetTtlMinutes,
            @Value("${app.account-token.secure-account-ttl-hours}") long secureAccountTtlHours,
            @Value("${app.account-token.deletion-ttl-minutes}") long deletionTtlMinutes) {
        this.accountTokenService = accountTokenService;
        this.emailSender = emailSender;
        this.frontendUrl = frontendUrl;
        this.passwordResetTtl = Duration.ofMinutes(passwordResetTtlMinutes);
        this.secureAccountTtl = Duration.ofHours(secureAccountTtlHours);
        this.deletionTtl = Duration.ofMinutes(deletionTtlMinutes);
    }

    public void sendPasswordReset(User user) {
        String link = link("/reset-password", accountTokenService.issue(
                user.getId(), AccountTokenPurpose.PASSWORD_RESET, passwordResetTtl));
        emailSender.send(new EmailMessage(
                user.getEmail(),
                "Reset your Ageguessr password",
                ("<p>Someone asked to reset the password of your Ageguessr account.</p>"
                                + "<p><a href=\"%s\">Choose a new password</a> (this link expires in 1 hour).</p>"
                                + "<p>If it wasn't you, ignore this email: your password stays the same.</p>")
                        .formatted(link)));
    }

    /**
     * Sent after every password change or reset. The link is a password-reset token
     * with a longer lifetime than a requested reset: if someone else changed the
     * password, the owner may only read this hours later, and it is their way back in
     * (resetting revokes every session, the intruder's included).
     */
    public void sendPasswordChanged(User user) {
        String link = link("/reset-password", accountTokenService.issue(
                user.getId(), AccountTokenPurpose.PASSWORD_RESET, secureAccountTtl));
        emailSender.send(new EmailMessage(
                user.getEmail(),
                "Your Ageguessr password was changed",
                ("<p>The password of your Ageguessr account was just changed.</p>"
                                + "<p>If it was you, there is nothing to do.</p>"
                                + "<p><strong>Wasn't you?</strong> <a href=\"%s\">Secure your account</a>: choose a"
                                + " new password and every device will be logged out (this link expires in"
                                + " 24 hours).</p>")
                        .formatted(link)));
    }

    public void sendDeletionConfirmation(User user) {
        String link = link("/confirm-account-deletion", accountTokenService.issue(
                user.getId(), AccountTokenPurpose.ACCOUNT_DELETION, deletionTtl));
        emailSender.send(new EmailMessage(
                user.getEmail(),
                "Confirm the deletion of your Ageguessr account",
                ("<p>You asked to delete your Ageguessr account. This cannot be undone.</p>"
                                + "<p><a href=\"%s\">Confirm the deletion</a> (this link expires in 1 hour).</p>"
                                + "<p>If it wasn't you, ignore this email and change your password: someone knows"
                                + " it.</p>")
                        .formatted(link)));
    }

    private String link(String path, String rawToken) {
        return frontendUrl + path + "?token=" + rawToken;
    }
}
