package app.ageguessr.features.auth.ResendVerificationEmail;

import app.ageguessr.features.auth.EmailVerificationService;
import app.ageguessr.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Always behaves identically to the caller whether the email doesn't exist or is
 * already verified — no leak, and no spam of an already-verified user's inbox.
 */
@Service
@RequiredArgsConstructor
public class ResendVerificationEmailHandler {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;

    public void handle(ResendVerificationEmailCommand command) {
        userRepository
                .findByEmailIgnoreCase(command.email())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(emailVerificationService::issueAndSend);
    }
}
