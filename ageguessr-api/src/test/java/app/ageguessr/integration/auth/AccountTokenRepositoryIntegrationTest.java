package app.ageguessr.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.AccountToken;
import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenRepository;
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
 * The single-use guarantee and the purpose isolation are JPQL bulk updates - only a
 * real database proves they touch exactly the rows they should. Bulk updates bypass
 * the persistence context, hence the entityManager.clear() before every read-back.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountTokenRepositoryIntegrationTest {

    @Autowired
    private AccountTokenRepository accountTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void aTokenCanOnlyBeMarkedConsumedOnce() {
        AccountToken token = token(user("once"), AccountTokenPurpose.PASSWORD_RESET, "once-hash");

        assertThat(accountTokenRepository.markConsumed(token.getId(), Instant.now())).isEqualTo(1);
        assertThat(accountTokenRepository.markConsumed(token.getId(), Instant.now())).isZero();
    }

    @Test
    void lookupFiltersOnPurpose() {
        token(user("purpose"), AccountTokenPurpose.ACCOUNT_DELETION, "deletion-hash");

        assertThat(accountTokenRepository.findByTokenHashAndPurpose("deletion-hash", AccountTokenPurpose.PASSWORD_RESET))
                .isEmpty();
        assertThat(accountTokenRepository.findByTokenHashAndPurpose("deletion-hash", AccountTokenPurpose.ACCOUNT_DELETION))
                .isPresent();
    }

    @Test
    void consumeAllOnlyTouchesTheGivenUserAndPurpose() {
        User owner = user("owner");
        User other = user("other");
        AccountToken ownerReset = token(owner, AccountTokenPurpose.PASSWORD_RESET, "owner-reset");
        AccountToken ownerDeletion = token(owner, AccountTokenPurpose.ACCOUNT_DELETION, "owner-deletion");
        AccountToken otherReset = token(other, AccountTokenPurpose.PASSWORD_RESET, "other-reset");

        accountTokenRepository.consumeAllActiveForUser(owner.getId(), AccountTokenPurpose.PASSWORD_RESET, Instant.now());
        entityManager.clear();

        assertThat(accountTokenRepository.findById(ownerReset.getId()).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(accountTokenRepository.findById(ownerDeletion.getId()).orElseThrow().getConsumedAt()).isNull();
        assertThat(accountTokenRepository.findById(otherReset.getId()).orElseThrow().getConsumedAt()).isNull();
    }

    private User user(String name) {
        return userRepository.saveAndFlush(User.builder()
                .email(name + "@x.com")
                .username(name)
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());
    }

    private AccountToken token(User user, AccountTokenPurpose purpose, String hash) {
        return accountTokenRepository.saveAndFlush(AccountToken.builder()
                .userId(user.getId())
                .purpose(purpose)
                .tokenHash(hash)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .build());
    }
}
