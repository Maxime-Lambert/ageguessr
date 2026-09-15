package app.ageguessr.features.auth.Login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AuthTokenService;
import app.ageguessr.features.auth.TokenPair;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ForbiddenException;
import app.ageguessr.shared.exceptions.UnauthorizedException;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String DUMMY_HASH = "dummy-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenService authTokenService;

    private LoginHandler handler;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        handler = new LoginHandler(userRepository, passwordEncoder, authTokenService, FIXED_CLOCK, 5, 15);
    }

    @Test
    void rejectsAnUnknownEmailWithTheGenericMessageAndNormalizesTiming() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new LoginCommand("ghost@x.com", "whatever")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches("whatever", DUMMY_HASH);
    }

    @Test
    void rejectsALockedAccountWithoutEverComparingTheRealPasswordHash() {
        User user = userWith("locked@x.com", "real-hash", true);
        user.setLockedUntil(FIXED_CLOCK.instant().plusSeconds(60));
        when(userRepository.findByEmailIgnoreCase("locked@x.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> handler.handle(new LoginCommand("locked@x.com", "whatever")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches("whatever", DUMMY_HASH);
        verify(passwordEncoder, never()).matches("whatever", "real-hash");
    }

    @Test
    void aLockThatHasAlreadyExpiredDoesNotBlockLogin() {
        User user = userWith("expired-lock@x.com", "real-hash", true);
        user.setLockedUntil(FIXED_CLOCK.instant().minusSeconds(1));
        when(userRepository.findByEmailIgnoreCase("expired-lock@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "real-hash")).thenReturn(true);
        when(authTokenService.issueAccessToken(user)).thenReturn("access-token");
        when(authTokenService.issueRefreshToken(user.getId())).thenReturn("refresh-token");

        TokenPair tokens = handler.handle(new LoginCommand("expired-lock@x.com", "correct"));

        assertThat(tokens.accessToken()).isEqualTo("access-token");
    }

    @Test
    void wrongPasswordIncrementsTheFailedAttemptCounter() {
        User user = userWith("user@x.com", "real-hash", true);
        user.setFailedLoginAttempts(2);
        when(userRepository.findByEmailIgnoreCase("user@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "real-hash")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginCommand("user@x.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    void theFifthConsecutiveFailureLocksTheAccount() {
        User user = userWith("user@x.com", "real-hash", true);
        user.setFailedLoginAttempts(4);
        when(userRepository.findByEmailIgnoreCase("user@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "real-hash")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginCommand("user@x.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(FIXED_CLOCK.instant().plusSeconds(15 * 60));
    }

    @Test
    void anUnverifiedEmailIsOnlyRevealedAfterTheCorrectPassword() {
        User user = userWith("unverified@x.com", "real-hash", false);
        when(userRepository.findByEmailIgnoreCase("unverified@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "real-hash")).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(new LoginCommand("unverified@x.com", "correct")))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(ex -> assertThat(((ForbiddenException) ex).getErrorCode()).isEqualTo("EMAIL_NOT_VERIFIED"));

        verify(authTokenService, never()).issueAccessToken(any());
    }

    @Test
    void aWrongPasswordOnAnUnverifiedAccountStillGetsTheGenericMessage() {
        User user = userWith("unverified@x.com", "real-hash", false);
        when(userRepository.findByEmailIgnoreCase("unverified@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "real-hash")).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(new LoginCommand("unverified@x.com", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void successResetsTheFailedAttemptCounterAndIssuesTokens() {
        User user = userWith("user@x.com", "real-hash", true);
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(null);
        when(userRepository.findByEmailIgnoreCase("user@x.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "real-hash")).thenReturn(true);
        when(authTokenService.issueAccessToken(user)).thenReturn("access-token");
        when(authTokenService.issueRefreshToken(user.getId())).thenReturn("refresh-token");

        TokenPair tokens = handler.handle(new LoginCommand("user@x.com", "correct"));

        assertThat(tokens).isEqualTo(new TokenPair("access-token", "refresh-token"));
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository, times(1)).save(user);
    }

    private User userWith(String email, String passwordHash, boolean emailVerified) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordHash)
                .emailVerified(emailVerified)
                .failedLoginAttempts(0)
                .createdAt(FIXED_CLOCK.instant())
                .build();
    }
}
