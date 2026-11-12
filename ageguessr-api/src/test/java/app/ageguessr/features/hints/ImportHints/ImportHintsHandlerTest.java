package app.ageguessr.features.hints.ImportHints;

import static app.ageguessr.features.hints.ImportHints.HintFixtures.french;
import static app.ageguessr.features.hints.ImportHints.HintFixtures.translated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
        assertThat(report).isEqualTo(new ImportHintsReport(1, 1, 0, 0, 0, 0, Map.of(), List.of()));
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
        assertThat(report).isEqualTo(new ImportHintsReport(0, 0, 0, 0, 0, 0, Map.of(), List.of()));
    }

    @Test
    void withoutTheOptionNothingIsRetired() {
        when(hints.findAllDedupeKeys()).thenReturn(Set.of("old"));

        ImportHintsReport report = handler.handle(List.of(french("new")));

        verify(hints, never()).setRetiredAt(anyCollection(), any());
        assertThat(report.retired()).isZero();
    }

    @Test
    void retiresTheKeysTheFilesNoLongerHold() {
        when(hints.findAllDedupeKeys()).thenReturn(Set.of("kept", "gone"));
        when(hints.setRetiredAt(List.of("gone"), NOW)).thenReturn(1);

        ImportHintsReport report = handler.handle(List.of(french("kept"), french("new")), true);

        verify(hints).setRetiredAt(List.of("gone"), NOW);
        assertThat(saved()).extracting(Hint::getDedupeKey).containsExactly("new");
        assertThat(report.retired()).isEqualTo(1);
        assertThat(report.reactivated()).isZero();
    }

    @Test
    void aRetiredKeyBackInTheFilesIsOfferedAgain() {
        when(hints.findAllDedupeKeys()).thenReturn(Set.of("back", "still-gone"));
        when(hints.findRetiredDedupeKeys()).thenReturn(Set.of("back", "still-gone"));
        when(hints.setRetiredAt(List.of("back"), null)).thenReturn(1);

        ImportHintsReport report = handler.handle(List.of(french("back")), true);

        verify(hints).setRetiredAt(List.of("back"), null);
        // Already retired: not retired a second time, its date stays the first one.
        verify(hints, never()).setRetiredAt(List.of("still-gone"), NOW);
        assertThat(report.reactivated()).isEqualTo(1);
    }

    @Test
    void aRefusedLineDoesNotRetireItsHint() {
        when(hints.findAllDedupeKeys()).thenReturn(Set.of("key"));

        ImportHintsReport report = handler.handle(List.of(HintFixtures.withTexts("FR", "Sorti en 1976.", null)), true);

        verify(hints, never()).setRetiredAt(List.of("key"), NOW);
        assertThat(report.refusedLines()).containsExactly("key: YEAR_IN_TEXT");
    }

    @Test
    void retiresByBatchesUnderTheBindParameterLimit() {
        Set<String> old = IntStream.range(0, ImportHintsHandler.RETIRE_BATCH + 1)
                .mapToObj(i -> "old-%05d".formatted(i))
                .collect(Collectors.toSet());
        when(hints.findAllDedupeKeys()).thenReturn(old);

        handler.handle(List.of(), true);

        verify(hints, times(2)).setRetiredAt(anyCollection(), eq(NOW));
    }

    @Test
    void namesTheSubjectOfAHintImportedWithoutOne() {
        HintFileEntry book = new HintFileEntry(
                "LITERATURE", 1976, "FR", "Parution : « Un roman » — Une autrice", null,
                "https://www.wikidata.org/wiki/Q1", "wikidata-notoriety", "Q42", "book", false);
        when(hints.findAllDedupeKeys()).thenReturn(Set.of("book", "other"));
        when(hints.findDedupeKeysWithoutSubject()).thenReturn(Set.of("book", "other"));
        when(hints.fillSubject("book", "Q42")).thenReturn(1);

        ImportHintsReport report = handler.handle(List.of(book, french("other")));

        verify(hints).fillSubject("book", "Q42");
        // A line without subject leaves the hint as it is.
        verify(hints, never()).fillSubject(eq("other"), any());
        assertThat(report.named()).isEqualTo(1);
        assertThat(report.alreadyImported()).isEqualTo(2);
    }
}
