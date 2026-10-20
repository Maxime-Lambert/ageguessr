package app.ageguessr.features.auth.ResetPassword;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.AccountLockout;
import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenRepository;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovery path, including after an account takeover: the new password takes effect,
 * every session is revoked (an intruder's included), the lockout is lifted and the
 * email counts as verified, since following the link proved access to the inbox.
 */
@Service
@RequiredArgsConstructor
public class ResetPasswordHandler {

    private final AccountTokenService accountTokenService;
    private final AccountTokenRepository accountTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountLockout accountLockout;
    private final AccountEmails accountEmails;
    private final Clock clock;

    @Transactional
    public void handle(ResetPasswordCommand command) {
        User user = accountTokenService.redeem(command.token(), AccountTokenPurpose.PASSWORD_RESET);
        Instant now = clock.instant();

        user.setPasswordHash(passwordEncoder.encode(command.newPassword()));
        user.setEmailVerified(true);
        accountLockout.clear(user);
        userRepository.save(user);

        // Any other reset link still in an inbox would let its holder undo this reset.
        accountTokenRepository.consumeAllActiveForUser(user.getId(), AccountTokenPurpose.PASSWORD_RESET, now);
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);

        accountEmails.sendPasswordChanged(user);
    }
}
