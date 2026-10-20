package app.ageguessr.features.auth;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * One lockout policy for every place a password is checked (login, and re-entering the
 * current password to change it or delete the account): a stolen session must not be a
 * way to brute-force the password without ever hitting the lock.
 */
@Component
public class AccountLockout {

    private final UserRepository userRepository;
    private final Clock clock;
    private final int maxAttempts;
    private final long durationMinutes;

    public AccountLockout(
            UserRepository userRepository,
            Clock clock,
            @Value("${app.lockout.max-attempts}") int maxAttempts,
            @Value("${app.lockout.duration-minutes}") long durationMinutes) {
        this.userRepository = userRepository;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.durationMinutes = durationMinutes;
    }

    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant());
    }

    /** Persists immediately: the caller is about to throw, so nothing else would save the counter. */
    public void recordFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxAttempts) {
            user.setLockedUntil(clock.instant().plus(Duration.ofMinutes(durationMinutes)));
        }
        userRepository.save(user);
    }

    /** Does not save - the caller saves the user along with its own changes. */
    public void clear(User user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }
}
