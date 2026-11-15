package app.ageguessr.features.auth;

import app.ageguessr.shared.exceptions.ForbiddenException;
import java.time.Clock;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks a code of the player's authenticator app. Like the password, a wrong code counts
 * toward the lockout (5 failures, 15 minutes): with 3 valid codes out of a million at any
 * time, guessing is hopeless. A code accepted once is refused after (replay).
 */
@Component
@RequiredArgsConstructor
public class TwoFactorVerifier {

    private final SecretCipher cipher;
    private final AccountLockout lockout;
    private final Clock clock;

    /**
     * On success, records the step and clears the failures on the user; the caller saves it
     * with its own changes.
     */
    public void verify(User user, String code) {
        if (user.getTotpSecret() == null || lockout.isLocked(user)) {
            throw invalid();
        }
        OptionalLong step = Totp.verify(
                cipher.decrypt(user.getTotpSecret()), code, clock.instant(), user.getTotpLastStep());
        if (step.isEmpty()) {
            lockout.recordFailure(user);
            throw invalid();
        }
        user.setTotpLastStep(step.getAsLong());
        lockout.clear(user);
    }

    private static ForbiddenException invalid() {
        return new ForbiddenException("INVALID_TOTP_CODE", "The code is incorrect or has expired");
    }
}
