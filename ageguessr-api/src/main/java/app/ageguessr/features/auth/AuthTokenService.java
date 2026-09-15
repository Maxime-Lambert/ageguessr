package app.ageguessr.features.auth;

import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Centralizes access/refresh token issuance so Login and Refresh don't each duplicate
 * the JWT claim-building and refresh-token-persistence logic.
 */
@Service
public class AuthTokenService {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Clock clock;
    private final String issuer;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    public AuthTokenService(
            JwtEncoder jwtEncoder,
            RefreshTokenRepository refreshTokenRepository,
            OpaqueTokenGenerator tokenGenerator,
            Clock clock,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes,
            @Value("${app.refresh-token.ttl-days}") long refreshTokenTtlDays) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
        this.issuer = issuer;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(accessTokenTtlMinutes)))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Persists a new refresh token row (hashed) for the user and returns the raw value. */
    public String issueRefreshToken(UUID userId) {
        OpaqueTokenGenerator.GeneratedToken token = tokenGenerator.generate();
        Instant now = clock.instant();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .tokenHash(token.hash())
                .expiresAt(now.plus(Duration.ofDays(refreshTokenTtlDays)))
                .createdAt(now)
                .build());
        return token.rawValue();
    }
}
