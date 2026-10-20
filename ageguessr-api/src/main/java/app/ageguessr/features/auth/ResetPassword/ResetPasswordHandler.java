package app.ageguessr.features.auth.ResetPassword;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.AccountLockout;
import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenRepository;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.PasswordPolicy;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Recovery path, including after an account takeover: the new password takes effect,
 * every session is revoked (an intruder's included), the lockout is lifted and the
 * email counts as verified, since following the link proved access to the inbox.
 *
 * The database work commits as one unit; the notification email is sent only after,
 * so a slow or failing email provider never holds the transaction (and its row locks)
 * open, nor rolls back a reset that already succeeded.
 */
@Service
@RequiredArgsConstructor
public class ResetPasswordHandler {

    private final AccountTokenService accountTokenService;
    private final AccountTokenRepository accountTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AccountLockout accountLockout;
    private final AccountEmails accountEmails;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void handle(ResetPasswordCommand command) {
        User user = transactionTemplate.execute(status -> resetInDatabase(command));
        accountEmails.sendPasswordChanged(user);
    }

    private User resetInDatabase(ResetPasswordCommand command) {
        User user = accountTokenService.redeem(command.token(), AccountTokenPurpose.PASSWORD_RESET);
        // A refused password rolls the redemption back: the link stays usable for a retry.
        passwordPolicy.check(command.newPassword(), user.getUsername(), user.getEmail());
        Instant now = clock.instant();

        user.setPasswordHash(passwordEncoder.encode(command.newPassword()));
        user.setEmailVerified(true);
        accountLockout.clear(user);
        userRepository.save(user);

        // Any other reset link still in an inbox would let its holder undo this reset.
        accountTokenRepository.consumeAllActiveForUser(user.getId(), AccountTokenPurpose.PASSWORD_RESET, now);
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
        return user;
    }
}
