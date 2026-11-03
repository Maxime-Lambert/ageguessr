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
    private final AccountLinks links;
    private final Duration passwordResetTtl;
    private final Duration secureAccountTtl;
    private final Duration deletionTtl;

    public AccountEmails(
            AccountTokenService accountTokenService,
            EmailSender emailSender,
            AccountLinks links,
            @Value("${app.account-token.password-reset-ttl-minutes}") long passwordResetTtlMinutes,
            @Value("${app.account-token.secure-account-ttl-hours}") long secureAccountTtlHours,
            @Value("${app.account-token.deletion-ttl-minutes}") long deletionTtlMinutes) {
        this.accountTokenService = accountTokenService;
        this.emailSender = emailSender;
        this.links = links;
        this.passwordResetTtl = Duration.ofMinutes(passwordResetTtlMinutes);
        this.secureAccountTtl = Duration.ofHours(secureAccountTtlHours);
        this.deletionTtl = Duration.ofMinutes(deletionTtlMinutes);
    }

    public void sendPasswordReset(User user) {
        String link = links.to(user, "/reset-password", accountTokenService.issue(
                user.getId(), AccountTokenPurpose.PASSWORD_RESET, passwordResetTtl));
        send(user, AccountEmailTexts.passwordReset(user.languageOrDefault(), link));
    }

    /**
     * Sent after every password change or reset. The link is a password-reset token
     * with a longer lifetime than a requested reset: if someone else changed the
     * password, the owner may only read this hours later, and it is their way back in
     * (resetting revokes every session, the intruder's included).
     */
    public void sendPasswordChanged(User user) {
        String link = links.to(user, "/reset-password", accountTokenService.issue(
                user.getId(), AccountTokenPurpose.PASSWORD_RESET, secureAccountTtl));
        send(user, AccountEmailTexts.passwordChanged(user.languageOrDefault(), link));
    }

    public void sendDeletionConfirmation(User user) {
        String link = links.to(user, "/confirm-account-deletion", accountTokenService.issue(
                user.getId(), AccountTokenPurpose.ACCOUNT_DELETION, deletionTtl));
        send(user, AccountEmailTexts.deletionConfirmation(user.languageOrDefault(), link));
    }

    private void send(User user, AccountEmailTexts.Text text) {
        emailSender.send(new EmailMessage(user.getEmail(), text.subject(), text.html()));
    }
}
