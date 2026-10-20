package app.ageguessr.features.auth.Register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.EmailVerificationService;
import app.ageguessr.features.auth.PasswordPolicy;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegisterHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Test
    void hashesThePasswordAndPersistsAnUnverifiedUser() {
        when(userRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("NewPlayer")).thenReturn(false);
        when(passwordEncoder.encode("tortoise-violin-42")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        RegisterHandler handler =
                new RegisterHandler(userRepository, passwordPolicy, passwordEncoder, emailVerificationService, FIXED_CLOCK);
        RegisterResponse response = handler.handle(new RegisterCommand("new@x.com", "NewPlayer", "tortoise-violin-42"));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("new@x.com");
        assertThat(savedUser.getValue().getUsername()).isEqualTo("NewPlayer");
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getValue().isEmailVerified()).isFalse();
        assertThat(savedUser.getValue().getCreatedAt()).isEqualTo(FIXED_CLOCK.instant());

        assertThat(response.email()).isEqualTo("new@x.com");
        assertThat(response.username()).isEqualTo("NewPlayer");
        verify(emailVerificationService).issueAndSend(savedUser.getValue());
    }

    @Test
    void rejectsADuplicateEmailWithoutSendingAnyEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@x.com")).thenReturn(true);

        RegisterHandler handler =
                new RegisterHandler(userRepository, passwordPolicy, passwordEncoder, emailVerificationService, FIXED_CLOCK);

        assertThatThrownBy(() -> handler.handle(new RegisterCommand("taken@x.com", "AnyName", "tortoise-violin-42")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("EMAIL_TAKEN");

        verify(userRepository, never()).save(any());
        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void rejectsADuplicateUsernameWithoutSendingAnyEmail() {
        when(userRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("Taken")).thenReturn(true);

        RegisterHandler handler =
                new RegisterHandler(userRepository, passwordPolicy, passwordEncoder, emailVerificationService, FIXED_CLOCK);

        assertThatThrownBy(() -> handler.handle(new RegisterCommand("new@x.com", "Taken", "tortoise-violin-42")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("USERNAME_TAKEN");

        verify(userRepository, never()).save(any());
        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void reportsTheEmailConflictFirstWhenBothAreTaken() {
        when(userRepository.existsByEmailIgnoreCase("taken@x.com")).thenReturn(true);

        RegisterHandler handler =
                new RegisterHandler(userRepository, passwordPolicy, passwordEncoder, emailVerificationService, FIXED_CLOCK);

        assertThatThrownBy(() -> handler.handle(new RegisterCommand("taken@x.com", "Taken", "tortoise-violin-42")))
                .extracting("errorCode")
                .isEqualTo("EMAIL_TAKEN");
    }

    /** A refused password must not reveal whether the email is taken. */
    @Test
    void aRefusedPasswordStopsBeforeTheUniquenessChecks() {
        doThrow(new ValidationException("PASSWORD_TOO_COMMON", "This password is too common"))
                .when(passwordPolicy)
                .check("qwerty123456", "NewPlayer", "taken@x.com");

        RegisterHandler handler =
                new RegisterHandler(userRepository, passwordPolicy, passwordEncoder, emailVerificationService, FIXED_CLOCK);

        assertThatThrownBy(() -> handler.handle(new RegisterCommand("taken@x.com", "NewPlayer", "qwerty123456")))
                .extracting("errorCode")
                .isEqualTo("PASSWORD_TOO_COMMON");

        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }
}
