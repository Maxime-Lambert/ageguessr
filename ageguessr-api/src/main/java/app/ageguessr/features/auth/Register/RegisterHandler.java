package app.ageguessr.features.auth.Register;

import app.ageguessr.features.auth.EmailVerificationService;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterHandler {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final Clock clock;

    public RegisterResponse handle(RegisterCommand command) {
        if (userRepository.existsByEmailIgnoreCase(command.email())) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .email(command.email())
                .passwordHash(passwordEncoder.encode(command.password()))
                .emailVerified(false)
                .failedLoginAttempts(0)
                .createdAt(clock.instant())
                .build();
        user = userRepository.save(user);

        emailVerificationService.issueAndSend(user);

        return new RegisterResponse(user.getId(), user.getEmail());
    }
}
