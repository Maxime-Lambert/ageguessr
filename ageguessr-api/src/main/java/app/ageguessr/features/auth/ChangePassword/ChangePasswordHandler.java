package app.ageguessr.features.auth.ChangePassword;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.CurrentPasswordVerifier;
import app.ageguessr.features.auth.PasswordPolicy;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Deliberately not @Transactional: a wrong current password is recorded by
 * CurrentPasswordVerifier right before it throws, and a transaction would roll that
 * failure count back - turning this endpoint into an unlimited password oracle.
 */
@Service
@RequiredArgsConstructor
public class ChangePasswordHandler {

    private final UserRepository userRepository;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final AccountEmails accountEmails;
    private final Clock clock;

    /** rawRefreshToken: the caller's own session, kept alive; null revokes every session. */
    public void handle(UUID userId, String rawRefreshToken, ChangePasswordCommand command) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        currentPasswordVerifier.verify(user, command.currentPassword());
        // Only once the current password is proven: otherwise this would answer
        // questions about the account to someone who does not own it.
        passwordPolicy.check(command.newPassword(), user.getUsername(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(command.newPassword()));
        userRepository.save(user);

        Instant now = clock.instant();
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            refreshTokenRepository.revokeAllActiveForUser(userId, now);
        } else {
            refreshTokenRepository.revokeAllActiveForUserExcept(userId, tokenGenerator.hash(rawRefreshToken), now);
        }

        accountEmails.sendPasswordChanged(user);
    }
}
