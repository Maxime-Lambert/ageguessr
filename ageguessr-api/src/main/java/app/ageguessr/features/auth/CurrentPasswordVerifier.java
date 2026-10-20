package app.ageguessr.features.auth;

import app.ageguessr.shared.exceptions.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Re-authentication for sensitive actions taken from an existing session. A locked
 * account and a wrong password get the same answer, and failures count toward the same
 * lockout as login.
 */
@Component
@RequiredArgsConstructor
public class CurrentPasswordVerifier {

    private final PasswordEncoder passwordEncoder;
    private final AccountLockout accountLockout;

    public void verify(User user, String presentedPassword) {
        if (accountLockout.isLocked(user)) {
            throw invalid();
        }
        if (!passwordEncoder.matches(presentedPassword, user.getPasswordHash())) {
            accountLockout.recordFailure(user);
            throw invalid();
        }
        accountLockout.clear(user);
    }

    private static ForbiddenException invalid() {
        return new ForbiddenException("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
    }
}
