package app.ageguessr.shared.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Never active under the "e2e" profile — the QA/Playwright journey uses
 * RecordingEmailSender instead, so no real Resend key or network call is needed to
 * run it.
 */
@Service
@Profile("!e2e")
public class ResendEmailSender implements EmailSender {

    private final Resend resend;
    private final String from;

    public ResendEmailSender(
            @Value("${app.email.resend-api-key}") String apiKey,
            @Value("${app.email.from}") String from) {
        this.resend = new Resend(apiKey);
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(message.to())
                .subject(message.subject())
                .html(message.htmlBody())
                .build();
        try {
            resend.emails().send(options);
        } catch (ResendException e) {
            throw new IllegalStateException("Failed to send email via Resend", e);
        }
    }
}
