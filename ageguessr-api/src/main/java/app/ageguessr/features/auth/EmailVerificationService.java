package app.ageguessr.features.auth;

import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Shared by Register and ResendVerificationEmail — both need to generate a fresh
 * verification token and email the confirmation link the same way.
 */
@Service
public class EmailVerificationService {

    private final OpaqueTokenGenerator tokenGenerator;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final EmailSender emailSender;
    private final Clock clock;
    private final long tokenTtlHours;
    private final AccountLinks links;

    public EmailVerificationService(
            OpaqueTokenGenerator tokenGenerator,
            EmailVerificationTokenRepository verificationTokenRepository,
            EmailSender emailSender,
            Clock clock,
            @Value("${app.email.verification-token-ttl-hours}") long tokenTtlHours,
            AccountLinks links) {
        this.tokenGenerator = tokenGenerator;
        this.verificationTokenRepository = verificationTokenRepository;
        this.emailSender = emailSender;
        this.clock = clock;
        this.tokenTtlHours = tokenTtlHours;
        this.links = links;
    }

    public void issueAndSend(User user) {
        OpaqueTokenGenerator.GeneratedToken token = tokenGenerator.generate();
        Instant now = clock.instant();
        verificationTokenRepository.save(EmailVerificationToken.builder()
                .userId(user.getId())
                .tokenHash(token.hash())
                .expiresAt(now.plus(Duration.ofHours(tokenTtlHours)))
                .createdAt(now)
                .build());

        String link = links.to(user, "/confirm-email", token.rawValue());
        AccountEmailTexts.Text text = AccountEmailTexts.emailVerification(user.languageOrDefault(), link);
        emailSender.send(new EmailMessage(user.getEmail(), text.subject(), text.html()));
    }
}
