package app.ageguessr.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.EmailVerificationToken;
import app.ageguessr.features.auth.EmailVerificationTokenRepository;
import app.ageguessr.features.auth.RefreshToken;
import app.ageguessr.features.auth.RefreshTokenRepository;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the ON DELETE CASCADE foreign keys from V1__create_auth_tables.sql — a
 * real database behavior, not something a mocked repository could exercise.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CascadeDeleteIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletingAUserCascadesToItsRefreshAndVerificationTokens() {
        User user = userRepository.saveAndFlush(
                User.builder()
                        .email("cascade@x.com")
                        .passwordHash("hash")
                        .createdAt(Instant.now())
                        .build());

        RefreshToken refreshToken = refreshTokenRepository.saveAndFlush(
                RefreshToken.builder()
                        .userId(user.getId())
                        .tokenHash("refresh-hash")
                        .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                        .createdAt(Instant.now())
                        .build());

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.saveAndFlush(
                EmailVerificationToken.builder()
                        .userId(user.getId())
                        .tokenHash("verification-hash")
                        .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                        .createdAt(Instant.now())
                        .build());

        userRepository.delete(user);
        userRepository.flush();
        // The cascade delete happens at the database level (ON DELETE CASCADE), not
        // through Hibernate's object graph (no @OneToMany mapping exists) — the
        // session's first-level cache still holds the now-stale RefreshToken/
        // EmailVerificationToken managed instances and would otherwise return them
        // from findById without a real query. Clearing forces a genuine round trip.
        entityManager.clear();

        assertThat(refreshTokenRepository.findById(refreshToken.getId())).isEmpty();
        assertThat(emailVerificationTokenRepository.findById(verificationToken.getId())).isEmpty();
    }

    @Test
    void deletingAUserWithNoTokensSucceeds() {
        User user = userRepository.saveAndFlush(
                User.builder()
                        .email("no-tokens@x.com")
                        .passwordHash("hash")
                        .createdAt(Instant.now())
                        .build());

        userRepository.delete(user);
        userRepository.flush();

        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void unrelatedUsersTokensSurviveTheDelete() {
        User survivor = userRepository.saveAndFlush(
                User.builder()
                        .email("survivor@x.com")
                        .passwordHash("hash")
                        .createdAt(Instant.now())
                        .build());
        RefreshToken survivorToken = refreshTokenRepository.saveAndFlush(
                RefreshToken.builder()
                        .userId(survivor.getId())
                        .tokenHash("survivor-hash")
                        .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                        .createdAt(Instant.now())
                        .build());

        User deleted = userRepository.saveAndFlush(
                User.builder()
                        .email("deleted@x.com")
                        .passwordHash("hash")
                        .createdAt(Instant.now())
                        .build());
        userRepository.delete(deleted);
        userRepository.flush();

        assertThat(refreshTokenRepository.findById(survivorToken.getId())).isPresent();
        assertThat(userRepository.findById(deleted.getId())).isEmpty();
    }
}
