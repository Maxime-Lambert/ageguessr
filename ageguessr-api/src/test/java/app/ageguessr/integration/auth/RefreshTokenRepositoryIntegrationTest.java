package app.ageguessr.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
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

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RefreshTokenRepositoryIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void revokeAllExceptKeepsOnlyTheGivenSessionOfThatUser() {
        User user = user("sessions");
        User other = user("bystander");
        RefreshToken current = token(user, "current");
        RefreshToken otherDevice = token(user, "other-device");
        RefreshToken bystander = token(other, "bystander");

        refreshTokenRepository.revokeAllActiveForUserExcept(user.getId(), "current", Instant.now());
        entityManager.clear();

        assertThat(refreshTokenRepository.findById(current.getId()).orElseThrow().getRevokedAt()).isNull();
        assertThat(refreshTokenRepository.findById(otherDevice.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshTokenRepository.findById(bystander.getId()).orElseThrow().getRevokedAt()).isNull();
    }

    private User user(String name) {
        return userRepository.saveAndFlush(User.builder()
                .email(name + "@x.com")
                .username(name)
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());
    }

    private RefreshToken token(User user, String hash) {
        return refreshTokenRepository.saveAndFlush(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash)
                .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build());
    }
}
