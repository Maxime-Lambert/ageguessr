package app.ageguessr.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.auth.UserRole;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the real database constraint from V1__create_auth_tables.sql (unique
 * indexes on LOWER(email) and, since V2, LOWER(username)) — not something Mockito could ever catch, since it's a
 * Postgres-level guarantee, not application code.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void anAccountIsARegularUserByDefault() {
        User saved = userRepository.saveAndFlush(User.builder()
                .email("role-default@x.com")
                .username("role-default")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());

        assertThat(userRepository.findById(saved.getId())).get().extracting(User::getRole).isEqualTo(UserRole.USER);
    }

    @Test
    void theDatabaseRefusesAnUnknownRole() {
        User saved = userRepository.saveAndFlush(User.builder()
                .email("role-check@x.com")
                .username("role-check")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());

        assertThatThrownBy(() -> {
                    entityManager
                            .createNativeQuery("UPDATE users SET role = 'ROOT' WHERE id = :id")
                            .setParameter("id", saved.getId())
                            .executeUpdate();
                    entityManager.flush();
                })
                .hasStackTraceContaining("users_role");
    }

    @Test
    void rejectsCaseInsensitiveDuplicateEmail() {
        userRepository.saveAndFlush(newUser("foo@x.com", "first"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("Foo@X.com", "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsAUserRegardlessOfEmailCase() {
        userRepository.saveAndFlush(newUser("foo@x.com", "foo"));

        assertThat(userRepository.findByEmailIgnoreCase("FOO@X.COM")).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase("FOO@X.COM")).isTrue();
    }

    @Test
    void rejectsCaseInsensitiveDuplicateUsername() {
        userRepository.saveAndFlush(newUser("one@x.com", "Max"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("two@x.com", "mAX")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usernameLookupsIgnoreCaseAndCanExcludeTheCurrentUser() {
        User max = userRepository.saveAndFlush(newUser("max@x.com", "Max"));

        assertThat(userRepository.existsByUsernameIgnoreCase("MAX")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCaseAndIdNot("max", max.getId())).isFalse();

        User other = userRepository.saveAndFlush(newUser("other@x.com", "other"));
        assertThat(userRepository.existsByUsernameIgnoreCaseAndIdNot("MAX", other.getId())).isTrue();
    }

    private User newUser(String email, String username) {
        return User.builder()
                .email(email)
                .username(username)
                .passwordHash("hash")
                .emailVerified(false)
                .failedLoginAttempts(0)
                .createdAt(Instant.now())
                .build();
    }
}
