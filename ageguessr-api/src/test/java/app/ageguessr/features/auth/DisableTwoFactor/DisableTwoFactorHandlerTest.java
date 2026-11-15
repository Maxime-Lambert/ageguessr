package app.ageguessr.features.auth.DisableTwoFactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.CurrentPasswordVerifier;
import app.ageguessr.features.auth.TwoFactorVerifier;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.ForbiddenException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisableTwoFactorHandlerTest {

    @Mock
    private UserRepository users;

    @Mock
    private CurrentPasswordVerifier passwordVerifier;

    @Mock
    private TwoFactorVerifier twoFactor;

    @Mock
    private AccountEmails emails;

    @InjectMocks
    private DisableTwoFactorHandler handler;

    private User userWithTwoFactor() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .totpSecret("encrypted")
                .totpEnabledAt(Instant.now())
                .totpLastStep(42L)
                .build();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void checksThePasswordWithoutClearingTheFailuresThenTheCode() {
        User user = userWithTwoFactor();

        handler.handle(user.getId(), new DisableTwoFactorCommand("password", "123456"));

        // The failures are cleared by the code check alone: the password must not reset them.
        verify(passwordVerifier).verifyKeepingFailures(user, "password");
        verify(passwordVerifier, never()).verify(any(), any());
        verify(twoFactor).verify(user, "123456");
        assertThat(user.hasTwoFactor()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
        verify(emails).sendTwoFactorDisabled(user);
    }

    @Test
    void aWrongCodeKeepsItOn() {
        User user = userWithTwoFactor();
        doThrow(new ForbiddenException("INVALID_TOTP_CODE", "nope")).when(twoFactor).verify(user, "000000");

        assertThatThrownBy(() -> handler.handle(user.getId(), new DisableTwoFactorCommand("password", "000000")))
                .isInstanceOf(ForbiddenException.class);

        assertThat(user.hasTwoFactor()).isTrue();
        verify(users, never()).save(any());
    }

    @Test
    void refusesWhenItIsAlreadyOff() {
        User user = User.builder().id(UUID.randomUUID()).build();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> handler.handle(user.getId(), new DisableTwoFactorCommand("password", "123456")))
                .isInstanceOf(ConflictException.class);
    }
}
