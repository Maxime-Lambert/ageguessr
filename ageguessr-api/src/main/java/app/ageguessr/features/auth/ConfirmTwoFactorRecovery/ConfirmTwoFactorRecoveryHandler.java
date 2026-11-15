package app.ageguessr.features.auth.ConfirmTwoFactorRecovery;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The emailed link, confirmed by an explicit click (link scanners open the emails):
 * two-factor off, every session revoked, the owner told.
 */
@Service
@RequiredArgsConstructor
public class ConfirmTwoFactorRecoveryHandler {

    private final AccountTokenService accountTokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccountEmails emails;
    private final Clock clock;

    @Transactional
    public void handle(ConfirmTwoFactorRecoveryCommand command) {
        User user = accountTokens.redeem(command.token(), AccountTokenPurpose.TOTP_RECOVERY);
        user.dropTwoFactor();
        users.save(user);
        refreshTokens.revokeAllActiveForUser(user.getId(), clock.instant());
        emails.sendTwoFactorDisabled(user);
    }
}
