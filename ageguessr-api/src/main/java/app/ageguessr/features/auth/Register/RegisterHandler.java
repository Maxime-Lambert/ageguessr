package app.ageguessr.features.auth.Register;

import app.ageguessr.features.auth.EmailVerificationService;
import app.ageguessr.features.auth.PasswordPolicy;
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
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final Clock clock;

    public RegisterResponse handle(RegisterCommand command) {
        // Before the uniqueness checks: a refused password must not reveal a taken email.
        passwordPolicy.check(command.password(), command.username(), command.email());
        if (userRepository.existsByEmailIgnoreCase(command.email())) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        if (userRepository.existsByUsernameIgnoreCase(command.username())) {
            throw new ConflictException("USERNAME_TAKEN", "This username is already taken");
        }

        User user = User.builder()
                .email(command.email())
                .username(command.username())
                .passwordHash(passwordEncoder.encode(command.password()))
                .emailVerified(false)
                .failedLoginAttempts(0)
                .createdAt(clock.instant())
                .build();
        user = userRepository.save(user);

        emailVerificationService.issueAndSend(user);

        return new RegisterResponse(user.getId(), user.getEmail(), user.getUsername());
    }
}
