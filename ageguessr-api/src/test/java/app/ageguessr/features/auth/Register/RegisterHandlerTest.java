package app.ageguessr.features.auth.Register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.EmailVerificationService;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
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

    @Test
    void hashesThePasswordAndPersistsAnUnverifiedUser() {
        when(userRepository.existsByEmailIgnoreCase("new@x.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        RegisterHandler handler =
                new RegisterHandler(userRepository, passwordEncoder, emailVerificationService, FIXED_CLOCK);
        RegisterResponse response = handler.handle(new RegisterCommand("new@x.com", "password123"));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getEmail()).isEqualTo("new@x.com");
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getValue().isEmailVerified()).isFalse();
        assertThat(savedUser.getValue().getCreatedAt()).isEqualTo(FIXED_CLOCK.instant());

        assertThat(response.email()).isEqualTo("new@x.com");
        verify(emailVerificationService).issueAndSend(savedUser.getValue());
    }

    @Test
    void rejectsADuplicateEmailWithoutSendingAnyEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@x.com")).thenReturn(true);

        RegisterHandler handler =
                new RegisterHandler(userRepository, passwordEncoder, emailVerificationService, FIXED_CLOCK);

        assertThatThrownBy(() -> handler.handle(new RegisterCommand("taken@x.com", "password123")))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
        verify(emailVerificationService, never()).issueAndSend(any());
    }
}
