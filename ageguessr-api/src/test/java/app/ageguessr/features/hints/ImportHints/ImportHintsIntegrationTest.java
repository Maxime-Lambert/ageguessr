package app.ageguessr.features.hints.ImportHints;

import static app.ageguessr.features.hints.ImportHints.HintFixtures.french;
import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the import against a real Postgres. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ImportHintsIntegrationTest {

    @Autowired
    private ImportHintsHandler handler;

    @Autowired
    private HintFileReader reader;

    @Autowired
    private HintRepository hints;

    @Autowired
    private EntityManager entityManager;

    @Test
    void importingTwiceAddsNothingTheSecondTime() {
        List<HintFileEntry> entries = List.of(french("a"), french("b"));

        assertThat(handler.handle(entries).added()).isEqualTo(2);
        entityManager.flush();
        ImportHintsReport second = handler.handle(entries);

        assertThat(second.added()).isZero();
        assertThat(second.alreadyImported()).isEqualTo(2);
        assertThat(hints.count()).isEqualTo(2);
    }

    @Test
    void importingAgainKeepsACurationDecisionAndACorrectedText() {
        handler.handle(List.of(french("a")));
        entityManager.flush();
        Hint hint = hints.findAll().getFirst();
        hint.setReviewStatus(HintReviewStatus.REJECTED);
        hint.setTextFr("Texte corrigé.");
        hints.saveAndFlush(hint);
        entityManager.clear();

        handler.handle(List.of(french("a")));
        entityManager.flush();
        entityManager.clear();

        Hint reloaded = hints.findAll().getFirst();
        assertThat(reloaded.getReviewStatus()).isEqualTo(HintReviewStatus.REJECTED);
        assertThat(reloaded.getTextFr()).isEqualTo("Texte corrigé.");
    }

    @Test
    void everyLineOfTheShippedFilesIsAccepted() {
        List<HintFileEntry> entries = reader.readAll();

        ImportHintsReport report = handler.handle(entries);
        entityManager.flush();

        assertThat(report.refusedLines()).isEmpty();
        assertThat(report.added()).isEqualTo(entries.size()).isPositive();
    }
}
