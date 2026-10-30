package app.ageguessr.integration.hints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the constraints of V11__create_hints.sql against a real Postgres. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HintConstraintsIntegrationTest {

    @Autowired
    private HintRepository hints;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    private static Hint.HintBuilder valid() {
        return Hint.builder()
                .category(HintCategory.MUSIC)
                .year(1976)
                .audience(HintAudience.FR)
                .textFr("Un texte.")
                .sourceUrl("https://fr.wikipedia.org/wiki/Page")
                .sourceKind("chart")
                .dedupeKey("key")
                .reviewStatus(HintReviewStatus.APPROVED)
                .createdAt(Instant.now());
    }

    private static Arguments refused(String name, UnaryOperator<Hint.HintBuilder> change) {
        return Arguments.of(name, change);
    }

    /** One transaction per case: Postgres refuses every statement after a failed one. */
    static Stream<Arguments> invalidHints() {
        return Stream.of(
                refused("ALL without English", h -> h.audience(HintAudience.ALL)),
                refused("FR with English", h -> h.textEn("A text.")),
                refused("FR without French", h -> h.textFr(null)),
                refused("EN with French", h -> h.audience(HintAudience.EN).textEn("A text.")),
                refused("year too early", h -> h.year(1899)),
                refused("year too late", h -> h.year(2100)),
                refused("subject outside the celebrities", h -> h.subjectQid("Q42")),
                refused("celebrity without a subject", h -> h.category(HintCategory.CELEBRITIES)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidHints")
    void theDatabaseRefuses(String name, UnaryOperator<Hint.HintBuilder> change) {
        Hint hint = change.apply(valid()).build();

        assertThatThrownBy(() -> hints.saveAndFlush(hint)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseAcceptsAHintOfEachAudienceAndACelebrity() {
        List<Hint> saved = hints.saveAllAndFlush(List.of(
                valid().dedupeKey("fr").build(),
                valid().dedupeKey("en").audience(HintAudience.EN).textFr(null).textEn("A text.").build(),
                valid().dedupeKey("all").audience(HintAudience.ALL).textEn("A text.").build(),
                valid().dedupeKey("celebrity")
                        .category(HintCategory.CELEBRITIES)
                        .subjectQid("Q42")
                        .build()));

        assertThat(saved).hasSize(4);
    }

    @Test
    void theDatabaseRefusesASecondHintWithTheSameKey() {
        hints.saveAndFlush(valid().build());

        assertThatThrownBy(() -> hints.saveAndFlush(valid().build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheAdminKeepsTheHintAndItsDecision() {
        User admin = users.saveAndFlush(User.builder()
                .email("curator@x.com")
                .username("curator")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());
        Hint hint = hints.saveAndFlush(
                valid().reviewedBy(admin.getId()).reviewedAt(Instant.now()).build());

        users.delete(admin);
        users.flush();
        entityManager.clear();

        Hint reloaded = hints.findById(hint.getId()).orElseThrow();
        assertThat(reloaded.getReviewedBy()).isNull();
        assertThat(reloaded.getReviewedAt()).isNotNull();
        assertThat(reloaded.getReviewStatus()).isEqualTo(HintReviewStatus.APPROVED);
    }
}
