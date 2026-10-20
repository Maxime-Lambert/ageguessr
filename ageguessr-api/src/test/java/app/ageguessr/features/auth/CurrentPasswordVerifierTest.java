package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.shared.exceptions.ForbiddenException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CurrentPasswordVerifierTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserRepository userRepository;

    private CurrentPasswordVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new CurrentPasswordVerifier(passwordEncoder, new AccountLockout(userRepository, FIXED_CLOCK, 5, 15));
    }

    @Test
    void acceptsTheRightPasswordAndClearsPreviousFailures() {
        User user = User.builder().passwordHash("hash").failedLoginAttempts(3).build();
        when(passwordEncoder.matches("right", "hash")).thenReturn(true);

        verifier.verify(user, "right");

        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void aWrongPasswordCountsTowardTheLoginLockout() {
        User user = User.builder().passwordHash("hash").failedLoginAttempts(4).build();
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> verifier.verify(user, "wrong"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_CURRENT_PASSWORD");

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isAfter(FIXED_CLOCK.instant());
        verify(userRepository).save(user);
    }

    @Test
    void aLockedAccountIsRejectedEvenWithTheRightPassword() {
        User user = User.builder()
                .passwordHash("hash")
                .lockedUntil(FIXED_CLOCK.instant().plusSeconds(60))
                .build();

        assertThatThrownBy(() -> verifier.verify(user, "right"))
                .isInstanceOf(ForbiddenException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_CURRENT_PASSWORD");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
