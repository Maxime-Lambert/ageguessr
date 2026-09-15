package app.ageguessr.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the real database constraint from V1__create_auth_tables.sql (a unique
 * index on LOWER(email)) — not something Mockito could ever catch, since it's a
 * Postgres-level guarantee, not application code.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void rejectsCaseInsensitiveDuplicateEmail() {
        userRepository.saveAndFlush(newUser("foo@x.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("Foo@X.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsAUserRegardlessOfEmailCase() {
        userRepository.saveAndFlush(newUser("foo@x.com"));

        assertThat(userRepository.findByEmailIgnoreCase("FOO@X.COM")).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase("FOO@X.COM")).isTrue();
    }

    private User newUser(String email) {
        return User.builder()
                .email(email)
                .passwordHash("hash")
                .emailVerified(false)
                .failedLoginAttempts(0)
                .createdAt(Instant.now())
                .build();
    }
}
