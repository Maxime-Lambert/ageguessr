package app.ageguessr.features.auth.LoginTotp;

import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.AuthTokenService;
import app.ageguessr.features.auth.TokenPair;
import app.ageguessr.features.auth.TwoFactorVerifier;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The second step of a two-factor sign-in: the challenge (the password was right, 5
 * minutes ago at most) and a code of the app. A wrong code keeps the challenge, for a
 * typo, and counts toward the lockout; the right one uses it up and signs in. Not
 * @Transactional, like the password checks: the failure recorded must survive the 403.
 */
@Service
@RequiredArgsConstructor
public class LoginTotpHandler {

    private final AccountTokenService accountTokens;
    private final TwoFactorVerifier twoFactor;
    private final UserRepository users;
    private final AuthTokenService authTokens;
    private final Clock clock;

    public TokenPair handle(LoginTotpCommand command) {
        User user = accountTokens.find(command.challenge(), AccountTokenPurpose.TOTP_LOGIN);
        twoFactor.verify(user, command.code());
        // Once only: two requests with the same right code, the second gets the 404.
        accountTokens.redeem(command.challenge(), AccountTokenPurpose.TOTP_LOGIN);
        users.save(user);
        users.markActive(user.getId(), clock.instant());
        return new TokenPair(authTokens.issueAccessToken(user), authTokens.issueRefreshToken(user.getId()));
    }
}
