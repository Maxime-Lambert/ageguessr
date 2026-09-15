package app.ageguessr.features.auth.Refresh;

import app.ageguessr.features.auth.AuthTokenService;
import app.ageguessr.features.auth.RefreshToken;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.TokenPair;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.UnauthorizedException;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshHandler {

    private static final String INVALID_TOKEN_MESSAGE = "Invalid refresh token";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    public TokenPair handle(RefreshCommand command) {
        String hash = tokenGenerator.hash(command.rawToken());
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MESSAGE));

        if (token.getRevokedAt() != null) {
            // A previously-rotated token being presented again is a theft signal (a
            // legitimate client never replays an already-rotated token) - revoke every
            // active session for this user defensively, not just this one request.
            refreshTokenRepository.revokeAllActiveForUser(token.getUserId(), clock.instant());
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        if (token.getExpiresAt().isBefore(clock.instant())) {
            throw new UnauthorizedException(INVALID_TOKEN_MESSAGE);
        }

        User user = userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN_MESSAGE));

        token.setRevokedAt(clock.instant());
        refreshTokenRepository.save(token);

        String accessToken = authTokenService.issueAccessToken(user);
        String newRefreshToken = authTokenService.issueRefreshToken(user.getId());
        return new TokenPair(accessToken, newRefreshToken);
    }
}
