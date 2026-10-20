package app.ageguessr.features.auth.ResetPassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.AccountLockout;
import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenRepository;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.PasswordPolicy;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ResetPasswordHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private AccountTokenService accountTokenService;

    @Mock
    private AccountTokenRepository accountTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private AccountEmails accountEmails;

    private ResetPasswordHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResetPasswordHandler(
                accountTokenService,
                accountTokenRepository,
                refreshTokenRepository,
                userRepository,
                passwordEncoder,
                passwordPolicy,
                new AccountLockout(userRepository, FIXED_CLOCK, 5, 15),
                accountEmails,
                // Runs the callback inline: the transaction boundary itself is exercised
                // by PasswordResetFunctionalTest against a real database.
                new TransactionTemplate(new PseudoTransactionManager()),
                FIXED_CLOCK);
    }

    @Test
    void setsTheNewPasswordLiftsTheLockVerifiesTheEmailAndLogsOutEverywhere() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .passwordHash("old-hash")
                .emailVerified(false)
                .failedLoginAttempts(5)
                .lockedUntil(FIXED_CLOCK.instant().plusSeconds(600))
                .build();
        when(accountTokenService.redeem("raw", AccountTokenPurpose.PASSWORD_RESET)).thenReturn(user);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        handler.handle(new ResetPasswordCommand("raw", "new-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
        verify(accountTokenRepository)
                .consumeAllActiveForUser(user.getId(), AccountTokenPurpose.PASSWORD_RESET, FIXED_CLOCK.instant());
        verify(refreshTokenRepository).revokeAllActiveForUser(user.getId(), FIXED_CLOCK.instant());
        verify(accountEmails).sendPasswordChanged(user);
    }

    @Test
    void anInvalidTokenChangesNothing() {
        when(accountTokenService.redeem("bad", AccountTokenPurpose.PASSWORD_RESET))
                .thenThrow(new NotFoundException("invalid"));

        assertThatThrownBy(() -> handler.handle(new ResetPasswordCommand("bad", "new-password")))
                .isInstanceOf(NotFoundException.class);

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
        verify(accountEmails, never()).sendPasswordChanged(any());
    }

    @Test
    void aRefusedPasswordChangesNothing() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("Player")
                .email("player@x.com")
                .passwordHash("old-hash")
                .build();
        when(accountTokenService.redeem("raw", AccountTokenPurpose.PASSWORD_RESET)).thenReturn(user);
        doThrow(new ValidationException("PASSWORD_TOO_COMMON", "This password is too common"))
                .when(passwordPolicy)
                .check("qwerty123456", "Player", "player@x.com");

        assertThatThrownBy(() -> handler.handle(new ResetPasswordCommand("raw", "qwerty123456")))
                .isInstanceOf(ValidationException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
        verify(accountEmails, never()).sendPasswordChanged(any());
    }

    /** Minimal no-op transaction manager, so TransactionTemplate can run without a database. */
    private static final class PseudoTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
