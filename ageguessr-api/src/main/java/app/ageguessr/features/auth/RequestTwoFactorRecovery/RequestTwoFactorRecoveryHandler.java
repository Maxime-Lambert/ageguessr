package app.ageguessr.features.auth.RequestTwoFactorRecovery;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.AccountTokenRepository;
import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.User;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * "I lost my authenticator app", from the code step: the challenge proves the password,
 * and the emailed link (1 hour) turns two-factor off (choice of 2026-11-15: no backup
 * codes). Whoever holds the password and the mailbox can therefore turn it off; the
 * owner is told by email when it happens.
 */
@Service
@RequiredArgsConstructor
public class RequestTwoFactorRecoveryHandler {

    private final AccountTokenService accountTokens;
    private final AccountTokenRepository tokenRepository;
    private final AccountEmails emails;
    private final Clock clock;

    public void handle(RequestTwoFactorRecoveryCommand command) {
        User user = accountTokens.find(command.challenge(), AccountTokenPurpose.TOTP_LOGIN);
        // Only the latest link works: an older one, clicked later, would sign out again.
        tokenRepository.consumeAllActiveForUser(user.getId(), AccountTokenPurpose.TOTP_RECOVERY, clock.instant());
        emails.sendTwoFactorRecovery(user);
    }
}
