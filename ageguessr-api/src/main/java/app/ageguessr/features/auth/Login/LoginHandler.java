package app.ageguessr.features.auth.Login;

import app.ageguessr.features.auth.AuthTokenService;
import app.ageguessr.features.auth.TokenPair;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ForbiddenException;
import app.ageguessr.shared.exceptions.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * The core security decision of the whole auth feature — see
 * docs/decisions/architecture.md and plans/active-plan.md for the full reasoning.
 * Summary: not-found / locked / wrong-password all produce the exact same
 * UnauthorizedException (same HTTP status, same message) so a caller can never
 * distinguish them; only an unverified email (which requires the *correct* password to
 * even reach that check) gets a distinct, machine-readable ForbiddenException.
 */
@Service
public class LoginHandler {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String DUMMY_PASSWORD = "dummy-password-for-timing-normalization";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final Clock clock;
    private final int lockoutMaxAttempts;
    private final long lockoutDurationMinutes;
    private final String dummyPasswordHash;

    public LoginHandler(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            Clock clock,
            @Value("${app.lockout.max-attempts}") int lockoutMaxAttempts,
            @Value("${app.lockout.duration-minutes}") long lockoutDurationMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.clock = clock;
        this.lockoutMaxAttempts = lockoutMaxAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
        // Computed once from the real encoder so its cost factor always matches real
        // password comparisons - not a hardcoded string that could drift out of sync.
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    public TokenPair handle(LoginCommand command) {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(command.email());

        if (maybeUser.isEmpty()) {
            normalizeTiming(command.password());
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        User user = maybeUser.get();

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant())) {
            normalizeTiming(command.password());
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        // Only reachable once the password is proven correct - an unverified email is
        // never revealed to someone who doesn't already know the password.
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Please verify your email before logging in");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String accessToken = authTokenService.issueAccessToken(user);
        String refreshToken = authTokenService.issueRefreshToken(user.getId());
        return new TokenPair(accessToken, refreshToken);
    }

    /** Runs a real bcrypt comparison against a throwaway hash so a short-circuited
     * rejection (no such user / already locked) takes roughly the same time as a real
     * password check, closing a timing side-channel that would otherwise let an
     * attacker distinguish those cases from a genuine wrong-password attempt. */
    private void normalizeTiming(String presentedPassword) {
        passwordEncoder.matches(presentedPassword, dummyPasswordHash);
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= lockoutMaxAttempts) {
            user.setLockedUntil(clock.instant().plus(Duration.ofMinutes(lockoutDurationMinutes)));
        }
        userRepository.save(user);
    }
}
