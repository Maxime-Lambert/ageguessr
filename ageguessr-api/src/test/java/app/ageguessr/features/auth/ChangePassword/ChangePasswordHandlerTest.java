package app.ageguessr.features.auth.ChangePassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.CurrentPasswordVerifier;
import app.ageguessr.features.auth.PasswordPolicy;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ForbiddenException;
import app.ageguessr.shared.exceptions.ValidationException;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ChangePasswordHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentPasswordVerifier currentPasswordVerifier;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccountEmails accountEmails;

    private final OpaqueTokenGenerator tokenGenerator = new OpaqueTokenGenerator();

    private ChangePasswordHandler handler;

    private User user;

    @BeforeEach
    void setUp() {
        handler = new ChangePasswordHandler(
                userRepository,
                currentPasswordVerifier,
                passwordPolicy,
                passwordEncoder,
                refreshTokenRepository,
                tokenGenerator,
                accountEmails,
                FIXED_CLOCK);
        user = User.builder().id(USER_ID).passwordHash("old-hash").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void changesThePasswordKeepsTheCurrentSessionAndNotifiesTheOwner() {
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        handler.handle(USER_ID, "current-refresh", new ChangePasswordCommand("old-password", "new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenRepository)
                .revokeAllActiveForUserExcept(USER_ID, tokenGenerator.hash("current-refresh"), FIXED_CLOCK.instant());
        verify(accountEmails).sendPasswordChanged(user);
    }

    @Test
    void withoutARefreshCookieEverySessionIsRevoked() {
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");

        handler.handle(USER_ID, null, new ChangePasswordCommand("old-password", "new-password"));

        verify(refreshTokenRepository).revokeAllActiveForUser(USER_ID, FIXED_CLOCK.instant());
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        doThrow(new ForbiddenException("INVALID_CURRENT_PASSWORD", "nope"))
                .when(currentPasswordVerifier)
                .verify(user, "wrong");

        assertThatThrownBy(() -> handler.handle(USER_ID, "current-refresh", new ChangePasswordCommand("wrong", "new-password")))
                .isInstanceOf(ForbiddenException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordPolicy, never()).check(anyString(), any(), any());
        verify(userRepository, never()).save(any());
        verify(accountEmails, never()).sendPasswordChanged(any());
    }

    @Test
    void checksTheNewPasswordOnlyOnceTheCurrentOneIsProven() {
        user.setUsername("Player");
        user.setEmail("player@x.com");
        when(passwordEncoder.encode("tortoise-violin-42")).thenReturn("new-hash");

        handler.handle(USER_ID, "current-refresh", new ChangePasswordCommand("old-password", "tortoise-violin-42"));

        InOrder order = inOrder(currentPasswordVerifier, passwordPolicy, passwordEncoder);
        order.verify(currentPasswordVerifier).verify(user, "old-password");
        order.verify(passwordPolicy).check("tortoise-violin-42", "Player", "player@x.com");
        order.verify(passwordEncoder).encode("tortoise-violin-42");
    }

    @Test
    void aRefusedNewPasswordChangesNothing() {
        doThrow(new ValidationException("PASSWORD_TOO_COMMON", "This password is too common"))
                .when(passwordPolicy)
                .check(anyString(), any(), any());

        assertThatThrownBy(() -> handler.handle(USER_ID, "current-refresh", new ChangePasswordCommand("old-password", "qwerty123456")))
                .isInstanceOf(ValidationException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
        verify(accountEmails, never()).sendPasswordChanged(any());
    }
}
