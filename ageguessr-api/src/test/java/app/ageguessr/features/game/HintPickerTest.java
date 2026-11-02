package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    void showsAHintInTheLanguageOfTheRoundWithItsYearAndSource() {
        Hint hint = hint(HintReviewStatus.APPROVED);
        when(hints.findById(hint.getId())).thenReturn(Optional.of(hint));

        assertThat(picker.shown(hint.getId(), Language.FR))
                .contains(new GameHint(HintCategory.MUSIC, "Un tube.", 1986, "https://fr.wikipedia.org/wiki/Tube"));
        assertThat(picker.shown(hint.getId(), Language.EN)).map(GameHint::text).contains("A hit.");
    }

    @Test
    void showsNoHintOnceRejected() {
        Hint hint = hint(HintReviewStatus.REJECTED);
        when(hints.findById(hint.getId())).thenReturn(Optional.of(hint));

        assertThat(picker.shown(hint.getId(), Language.EN)).isEmpty();
    }

    @Test
    void showsNoHintWhenThereWasNone() {
        assertThat(picker.shown(null, Language.EN)).isEmpty();
    }

    private static Hint hint(HintReviewStatus status) {
        return Hint.builder()
                .id(UUID.randomUUID())
                .category(HintCategory.MUSIC)
                .year(1986)
                .audience(HintAudience.ALL)
                .textEn("A hit.")
                .textFr("Un tube.")
                .sourceUrl("https://fr.wikipedia.org/wiki/Tube")
                .reviewStatus(status)
                .build();
    }
}
