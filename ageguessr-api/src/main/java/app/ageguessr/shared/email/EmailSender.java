package app.ageguessr.shared.email;

/**
 * Abstraction over the transactional email provider (Resend today). Kept generic
 * enough to be reused by future emails (password reset, friend notifications) —
 * those are out of scope for now, only email verification uses it currently.
 */
public interface EmailSender {
    void send(EmailMessage message);
}
