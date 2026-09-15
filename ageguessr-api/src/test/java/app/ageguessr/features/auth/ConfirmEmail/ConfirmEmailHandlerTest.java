package app.ageguessr.features.auth.ConfirmEmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.EmailVerificationToken;
import app.ageguessr.features.auth.EmailVerificationTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
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
class ConfirmEmailHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EmailVerificationTokenRepository verificationTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OpaqueTokenGenerator tokenGenerator;

    private ConfirmEmailHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConfirmEmailHandler(verificationTokenRepository, userRepository, tokenGenerator, FIXED_CLOCK);
    }

    @Test
    void rejectsATokenThatDoesNotExist() {
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ConfirmEmailCommand("raw")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsAnExpiredTokenWithTheSameNotFoundMessageAsAMissingOne() {
        EmailVerificationToken expired = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(FIXED_CLOCK.instant().minusSeconds(1))
                .build();
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> handler.handle(new ConfirmEmailCommand("raw")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("This confirmation link is invalid or has expired");
    }

    @Test
    void rejectsAnAlreadyConsumedTokenWithTheSameMessage() {
        EmailVerificationToken consumed = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash")
                .expiresAt(FIXED_CLOCK.instant().plusSeconds(3600))
                .consumedAt(FIXED_CLOCK.instant().minusSeconds(1))
                .build();
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> handler.handle(new ConfirmEmailCommand("raw")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("This confirmation link is invalid or has expired");
    }

    @Test
    void marksTheUserVerifiedAndConsumesTheTokenOnSuccess() {
        User user = User.builder().id(UUID.randomUUID()).emailVerified(false).build();
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .tokenHash("hash")
                .expiresAt(FIXED_CLOCK.instant().plusSeconds(3600))
                .build();
        when(tokenGenerator.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        handler.handle(new ConfirmEmailCommand("raw"));

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getConsumedAt()).isEqualTo(FIXED_CLOCK.instant());
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }
}
