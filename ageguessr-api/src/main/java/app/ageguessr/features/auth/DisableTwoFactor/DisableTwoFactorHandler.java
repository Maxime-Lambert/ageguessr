package app.ageguessr.features.auth.DisableTwoFactor;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.CurrentPasswordVerifier;
import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.TwoFactorVerifier;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turning two-factor off from the account: the password and a code, so that a session left
 * open (or stolen) is not enough. The app lost: the emailed link (ConfirmTwoFactorRecovery).
 */
@Service
@RequiredArgsConstructor
public class DisableTwoFactorHandler {

    private final UserRepository users;
    private final CurrentPasswordVerifier passwordVerifier;
    private final TwoFactorVerifier twoFactor;
    private final AccountEmails emails;

    public ProfileResponse handle(UUID userId, DisableTwoFactorCommand command) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (!user.hasTwoFactor()) {
            throw new ConflictException("TWO_FACTOR_NOT_ENABLED", "Two-factor authentication is off");
        }
        passwordVerifier.verifyKeepingFailures(user, command.password());
        twoFactor.verify(user, command.code());
        user.dropTwoFactor();
        users.save(user);
        emails.sendTwoFactorDisabled(user);
        return ProfileResponse.from(user);
    }
}
