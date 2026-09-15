package app.ageguessr.features.auth.ConfirmEmail;

import app.ageguessr.features.auth.EmailVerificationToken;
import app.ageguessr.features.auth.EmailVerificationTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfirmEmailHandler {

    private static final String INVALID_TOKEN_MESSAGE = "This confirmation link is invalid or has expired";

    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final UserRepository userRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Clock clock;

    public void handle(ConfirmEmailCommand command) {
        String hash = tokenGenerator.hash(command.token());
        EmailVerificationToken verificationToken = verificationTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new NotFoundException(INVALID_TOKEN_MESSAGE));

        Instant now = clock.instant();
        if (verificationToken.getConsumedAt() != null || verificationToken.getExpiresAt().isBefore(now)) {
            throw new NotFoundException(INVALID_TOKEN_MESSAGE);
        }

        User user = userRepository
                .findById(verificationToken.getUserId())
                .orElseThrow(() -> new NotFoundException(INVALID_TOKEN_MESSAGE));
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setConsumedAt(now);
        verificationTokenRepository.save(verificationToken);
    }
}
