package app.ageguessr.features.auth.Refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AuthTokenService;
import app.ageguessr.features.auth.RefreshToken;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.TokenPair;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.UnauthorizedException;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OpaqueTokenGenerator tokenGenerator;

    @Mock
    private AuthTokenService authTokenService;

    private RefreshHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RefreshHandler(refreshTokenRepository, userRepository, tokenGenerator, authTokenService, FIXED_CLOCK);
    }

    @Test
    void rejectsATokenThatDoesNotExist() {
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new RefreshCommand("raw")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void reusingAnAlreadyRevokedTokenRevokesEveryActiveSessionForThatUser() {
        UUID userId = UUID.randomUUID();
        RefreshToken revoked = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash("hash")
                .expiresAt(FIXED_CLOCK.instant().plusSeconds(3600))
                .revokedAt(FIXED_CLOCK.instant().minusSeconds(60))
                .build();
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> handler.handle(new RefreshCommand("raw")))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository).revokeAllActiveForUser(userId, FIXED_CLOCK.instant());
        verify(authTokenService, never()).issueAccessToken(any());
    }

    @Test
    void rejectsAnExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(FIXED_CLOCK.instant().minusSeconds(1))
                .build();
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> handler.handle(new RefreshCommand("raw")))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void rotatesAValidTokenIntoANewAccessAndRefreshTokenPair() {
        User user = User.builder().id(UUID.randomUUID()).email("user@x.com").build();
        RefreshToken valid = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash("hash")
                .expiresAt(FIXED_CLOCK.instant().plusSeconds(3600))
                .build();
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(valid));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(authTokenService.issueAccessToken(user)).thenReturn("new-access-token");
        when(authTokenService.issueRefreshToken(user.getId())).thenReturn("new-refresh-token");

        TokenPair tokens = handler.handle(new RefreshCommand("raw"));

        assertThat(tokens).isEqualTo(new TokenPair("new-access-token", "new-refresh-token"));
        assertThat(valid.getRevokedAt()).isEqualTo(FIXED_CLOCK.instant());
        verify(refreshTokenRepository).save(valid);
    }
}
