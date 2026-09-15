package app.ageguessr.features.auth.Logout;

import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutHandler {

    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Clock clock;

    /** Idempotent: a missing, garbage, or already-revoked token is not an error. */
    public void handle(LogoutCommand command) {
        if (command.rawToken() == null || command.rawToken().isBlank()) {
            return;
        }
        String hash = tokenGenerator.hash(command.rawToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(clock.instant());
                refreshTokenRepository.save(token);
            }
        });
    }
}
