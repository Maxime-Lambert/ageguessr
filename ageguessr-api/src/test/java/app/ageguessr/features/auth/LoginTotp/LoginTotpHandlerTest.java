package app.ageguessr.features.auth.LoginTotp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.AuthTokenService;
import app.ageguessr.features.auth.TokenPair;
import app.ageguessr.features.auth.TwoFactorVerifier;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginTotpHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AccountTokenService accountTokens;

    @Mock
    private TwoFactorVerifier twoFactor;

    @Mock
    private UserRepository users;

    @Mock
    private AuthTokenService authTokens;

    private LoginTotpHandler handler;
    private final User user = User.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        handler = new LoginTotpHandler(accountTokens, twoFactor, users, authTokens, CLOCK);
        when(accountTokens.find("challenge", AccountTokenPurpose.TOTP_LOGIN)).thenReturn(user);
    }

    @Test
    void theRightCodeUsesTheChallengeUpAndSignsIn() {
        when(authTokens.issueAccessToken(user)).thenReturn("access");
        when(authTokens.issueRefreshToken(user.getId())).thenReturn("refresh");

        TokenPair tokens = handler.handle(new LoginTotpCommand("challenge", "123456"));

        assertThat(tokens).isEqualTo(new TokenPair("access", "refresh"));
        verify(accountTokens).redeem("challenge", AccountTokenPurpose.TOTP_LOGIN);
        verify(users).save(user);
        verify(users).markActive(user.getId(), CLOCK.instant());
    }

    @Test
    void aWrongCodeKeepsTheChallengeAndSignsNothingIn() {
        doThrow(new ForbiddenException("INVALID_TOTP_CODE", "nope")).when(twoFactor).verify(user, "000000");

        assertThatThrownBy(() -> handler.handle(new LoginTotpCommand("challenge", "000000")))
                .isInstanceOf(ForbiddenException.class);

        verify(accountTokens, never()).redeem(any(), any());
        verify(authTokens, never()).issueAccessToken(any());
        verify(users, never()).markActive(any(), any());
    }
}
