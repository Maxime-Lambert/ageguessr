package app.ageguessr.features.hints.ImportHints;

import static app.ageguessr.features.hints.ImportHints.HintFixtures.french;
import static app.ageguessr.features.hints.ImportHints.HintFixtures.translated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImportHintsHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private final HintRepository hints = mock(HintRepository.class);
    private final ImportHintsHandler handler =
            new ImportHintsHandler(hints, Clock.fixed(NOW, ZoneOffset.UTC));

    @SuppressWarnings("unchecked")
    private List<Hint> saved() {
        ArgumentCaptor<List<Hint>> captor = ArgumentCaptor.forClass(List.class);
        verify(hints).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void anUntranslatedHintGoesStraightIntoTheGame() {
        ImportHintsReport report = handler.handle(List.of(french("a")));

        Hint hint = saved().getFirst();
        assertThat(hint.getReviewStatus()).isEqualTo(HintReviewStatus.APPROVED);
        assertThat(hint.getReviewedAt()).isNull();
        assertThat(hint.getReviewedBy()).isNull();
        assertThat(hint.getCreatedAt()).isEqualTo(NOW);
        assertThat(hint.getCategory()).isEqualTo(HintCategory.MUSIC);
        assertThat(hint.getAudience()).isEqualTo(HintAudience.FR);
        assertThat(hint.getYear()).isEqualTo(1976);
        assertThat(hint.getTextFr()).isEqualTo("Numéro un : « Une chanson ».");
        assertThat(hint.getTextEn()).isNull();
        assertThat(hint.getSourceUrl()).isEqualTo("https://fr.wikipedia.org/wiki/Page");
        assertThat(hint.getSourceKind()).isEqualTo("chart");
        assertThat(hint.getDedupeKey()).isEqualTo("a");
        assertThat(report).isEqualTo(new ImportHintsReport(1, 1, 0, Map.of(), List.of()));
    }

    @Test
    void aTranslatedHintWaitsForAnAdmin() {
        handler.handle(List.of(translated("a")));

        assertThat(saved().getFirst().getReviewStatus()).isEqualTo(HintReviewStatus.PENDING);
    }

    @Test
    void addsOnlyTheKeysNotImportedYet() {
        when(hints.findAllDedupeKeys()).thenReturn(Set.of("old"));

        ImportHintsReport report = handler.handle(List.of(french("old"), french("new")));

        assertThat(saved()).extracting(Hint::getDedupeKey).containsExactly("new");
        assertThat(report.added()).isEqualTo(1);
        assertThat(report.alreadyImported()).isEqualTo(1);
    }

    @Test
    void refusedLinesAreListedAndTheOthersStillGoIn() {
        HintFileEntry withYear = HintFixtures.withTexts("FR", "Sorti en 1976.", null);

        ImportHintsReport report = handler.handle(List.of(french(null), withYear, french("good")));

        assertThat(saved()).extracting(Hint::getDedupeKey).containsExactly("good");
        assertThat(report.read()).isEqualTo(3);
        assertThat(report.added()).isEqualTo(1);
        assertThat(report.refused())
                .containsOnly(Map.entry(HintRejection.MISSING_KEY, 1), Map.entry(HintRejection.YEAR_IN_TEXT, 1));
        assertThat(report.refusedLines()).containsExactly("line 1: MISSING_KEY", "key: YEAR_IN_TEXT");
    }

    @Test
    void theSecondLineWithAKeyIsRefused() {
        ImportHintsReport report = handler.handle(List.of(french("a"), french("a")));

        assertThat(saved()).hasSize(1);
        assertThat(report.refused()).containsOnly(Map.entry(HintRejection.DUPLICATE_KEY, 1));
    }

    @Test
    void anEmptyFileAddsNothing() {
        ImportHintsReport report = handler.handle(List.of());

        verify(hints).saveAll(anyList());
        assertThat(report).isEqualTo(new ImportHintsReport(0, 0, 0, Map.of(), List.of()));
    }
}
