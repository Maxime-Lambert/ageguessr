package app.ageguessr.features.auth;

import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Issues and redeems AccountTokens. Only the SHA-256 hash is stored; the raw value lives in the email link. */
@Service
@RequiredArgsConstructor
public class AccountTokenService {

    private static final String INVALID_TOKEN_MESSAGE = "This link is invalid or has expired";

    private final AccountTokenRepository accountTokenRepository;
    private final UserRepository userRepository;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Clock clock;

    /** Returns the raw token, to be put in an email link - it cannot be recovered later. */
    public String issue(UUID userId, AccountTokenPurpose purpose, Duration ttl) {
        OpaqueTokenGenerator.GeneratedToken token = tokenGenerator.generate();
        Instant now = clock.instant();
        accountTokenRepository.save(AccountToken.builder()
                .userId(userId)
                .purpose(purpose)
                .tokenHash(token.hash())
                .expiresAt(now.plus(ttl))
                .createdAt(now)
                .build());
        return token.rawValue();
    }

    /**
     * Marks the token used and returns its user. Unknown, wrong-purpose, expired and
     * already-used tokens are indistinguishable to the caller (same 404).
     */
    public User redeem(String rawToken, AccountTokenPurpose purpose) {
        AccountToken token = accountTokenRepository
                .findByTokenHashAndPurpose(tokenGenerator.hash(rawToken), purpose)
                .orElseThrow(() -> new NotFoundException(INVALID_TOKEN_MESSAGE));

        Instant now = clock.instant();
        if (token.getExpiresAt().isBefore(now) || accountTokenRepository.markConsumed(token.getId(), now) == 0) {
            throw new NotFoundException(INVALID_TOKEN_MESSAGE);
        }

        return userRepository
                .findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException(INVALID_TOKEN_MESSAGE));
    }
}
