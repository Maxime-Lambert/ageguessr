package app.ageguessr.features.auth.EnableTwoFactor;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.TwoFactorVerifier;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Second step: a first code proves the app holds the secret; two-factor is then on. */
@Service
@RequiredArgsConstructor
public class EnableTwoFactorHandler {

    private final UserRepository users;
    private final TwoFactorVerifier twoFactor;
    private final AccountEmails emails;
    private final Clock clock;

    public ProfileResponse handle(UUID userId, EnableTwoFactorCommand command) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.hasTwoFactor() || user.getTotpSecret() == null) {
            throw new ConflictException("TWO_FACTOR_NOT_PENDING", "Set up two-factor authentication first");
        }
        twoFactor.verify(user, command.code());
        user.setTotpEnabledAt(clock.instant());
        users.save(user);
        emails.sendTwoFactorEnabled(user);
        return ProfileResponse.from(user);
    }
}
