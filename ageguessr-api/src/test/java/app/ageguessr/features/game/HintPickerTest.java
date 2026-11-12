package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HintPickerTest {

    private final HintRepository hints = mock(HintRepository.class);
    private final HintPicker picker = new HintPicker(hints);

    @Test
    void readsTheHintsOfTheYearOfTheGameMinusTheAgeForTheLanguageAndWithoutThePerson() {
        when(hints.findEligible(1986, List.of(HintAudience.ALL, HintAudience.FR), "Q42")).thenReturn(List.of());

        picker.offerFor(LocalDate.of(2026, 11, 2), 40, Language.FR, "Q42");

        verify(hints).findEligible(1986, List.of(HintAudience.ALL, HintAudience.FR), "Q42");
    }

    @Test
    void showsTheHintsInTheLanguageOfTheRoundInTheirOrderWithYearAndSource() {
        Hint music = hint(HintCategory.MUSIC, HintReviewStatus.APPROVED);
        Hint film = hint(HintCategory.FILM, HintReviewStatus.APPROVED);
        // The repository answers in any order: the round's order is kept.
        when(hints.findAllById(List.of(music.getId(), film.getId()))).thenReturn(List.of(film, music));

        assertThat(picker.shown(List.of(music.getId(), film.getId()), Language.FR))
                .containsExactly(
                        new GameHint(HintCategory.MUSIC, "Un tube.", 1986, "https://fr.wikipedia.org/wiki/Tube"),
                        new GameHint(HintCategory.FILM, "Un tube.", 1986, "https://fr.wikipedia.org/wiki/Tube"));
        assertThat(picker.shown(List.of(music.getId(), film.getId()), Language.EN))
                .extracting(GameHint::text)
                .containsExactly("A hit.", "A hit.");
    }

    @Test
    void leavesOutAHintRejectedOrDeletedSince() {
        Hint kept = hint(HintCategory.MUSIC, HintReviewStatus.APPROVED);
        Hint rejected = hint(HintCategory.FILM, HintReviewStatus.REJECTED);
        UUID deleted = UUID.randomUUID();
        when(hints.findAllById(List.of(rejected.getId(), deleted, kept.getId()))).thenReturn(List.of(rejected, kept));
        // A null id: a hint deleted under the former ON DELETE SET NULL (V13, V20).
        List<UUID> ids = Arrays.asList(rejected.getId(), null, deleted, kept.getId());

        assertThat(picker.shown(ids, Language.EN)).extracting(GameHint::category).containsExactly(HintCategory.MUSIC);
    }

    @Test
    void showsNoHintWhenThereWasNone() {
        assertThat(picker.shown(List.of(), Language.EN)).isEmpty();
        verifyNoInteractions(hints);
    }

    private static Hint hint(HintCategory category, HintReviewStatus status) {
        return Hint.builder()
                .id(UUID.randomUUID())
                .category(category)
                .year(1986)
                .audience(HintAudience.ALL)
                .textEn("A hit.")
                .textFr("Un tube.")
                .sourceUrl("https://fr.wikipedia.org/wiki/Tube")
                .reviewStatus(status)
                .build();
    }
}
