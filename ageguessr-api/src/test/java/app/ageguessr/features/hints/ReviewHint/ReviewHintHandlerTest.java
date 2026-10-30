package app.ageguessr.features.hints.ReviewHint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewHintHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private final HintRepository hints = mock(HintRepository.class);
    private final ReviewHintHandler handler = new ReviewHintHandler(hints, Clock.fixed(NOW, ZoneOffset.UTC));

    private Hint stored(HintAudience audience, String textEn, String textFr, HintReviewStatus status) {
        Hint hint = Hint.builder()
                .id(UUID.randomUUID())
                .audience(audience)
                .textEn(textEn)
                .textFr(textFr)
                .reviewStatus(status)
                .build();
        when(hints.findById(hint.getId())).thenReturn(Optional.of(hint));
        return hint;
    }

    private static ReviewHintCommand decision(HintReviewStatus status) {
        return new ReviewHintCommand(status, null, null);
    }

    @Test
    void approvesATranslationAndRecordsWhoAndWhen() {
        Hint hint = stored(HintAudience.FR, null, "Un texte.", HintReviewStatus.PENDING);

        ReviewHintResponse response = handler.handle(ADMIN, hint.getId(), decision(HintReviewStatus.APPROVED));

        assertThat(response)
                .isEqualTo(new ReviewHintResponse(hint.getId(), HintReviewStatus.APPROVED, null, "Un texte."));
        assertThat(hint.getReviewedAt()).isEqualTo(NOW);
        assertThat(hint.getReviewedBy()).isEqualTo(ADMIN);
        verify(hints).save(hint);
    }

    @Test
    void rejectsAHintAlreadyInTheGame() {
        Hint hint = stored(HintAudience.FR, null, "Un texte.", HintReviewStatus.APPROVED);

        handler.handle(ADMIN, hint.getId(), decision(HintReviewStatus.REJECTED));

        assertThat(hint.getReviewStatus()).isEqualTo(HintReviewStatus.REJECTED);
    }

    @Test
    void undoGoesBackToPending() {
        Hint hint = stored(HintAudience.FR, null, "Un texte.", HintReviewStatus.REJECTED);

        handler.handle(ADMIN, hint.getId(), decision(HintReviewStatus.PENDING));

        assertThat(hint.getReviewStatus()).isEqualTo(HintReviewStatus.PENDING);
    }

    @Test
    void savesACorrectedText() {
        Hint hint = stored(HintAudience.ALL, "A text.", "Un texte.", HintReviewStatus.PENDING);

        ReviewHintResponse response = handler.handle(
                ADMIN, hint.getId(), new ReviewHintCommand(HintReviewStatus.APPROVED, "Fixed.", "Corrigé."));

        assertThat(hint.getTextEn()).isEqualTo("Fixed.");
        assertThat(hint.getTextFr()).isEqualTo("Corrigé.");
        assertThat(response.textEn()).isEqualTo("Fixed.");
        assertThat(response.textFr()).isEqualTo("Corrigé.");
    }

    private void assertRefused(Hint hint, ReviewHintCommand command, String code) {
        assertThatThrownBy(() -> handler.handle(ADMIN, hint.getId(), command))
                .isInstanceOfSatisfying(
                        ValidationException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
        verify(hints, never()).save(any());
    }

    @Test
    void refusesACorrectedTextWithAYear() {
        Hint english = stored(HintAudience.EN, "A text.", null, HintReviewStatus.PENDING);
        Hint french = stored(HintAudience.FR, null, "Un texte.", HintReviewStatus.PENDING);

        assertRefused(
                english,
                new ReviewHintCommand(HintReviewStatus.APPROVED, "Out in 1976.", null),
                ReviewHintHandler.HINT_TEXT_CONTAINS_YEAR);
        assertRefused(
                french,
                new ReviewHintCommand(HintReviewStatus.APPROVED, null, "Sorti en 1976."),
                ReviewHintHandler.HINT_TEXT_CONTAINS_YEAR);
    }

    @Test
    void refusesABlankText() {
        Hint hint = stored(HintAudience.FR, null, "Un texte.", HintReviewStatus.PENDING);

        assertRefused(
                hint,
                new ReviewHintCommand(HintReviewStatus.APPROVED, null, " "),
                ReviewHintHandler.HINT_TEXT_BLANK);
    }

    @Test
    void refusesATextInALanguageTheAudienceDoesNotRead() {
        Hint french = stored(HintAudience.FR, null, "Un texte.", HintReviewStatus.PENDING);
        Hint english = stored(HintAudience.EN, "A text.", null, HintReviewStatus.PENDING);

        assertRefused(
                french,
                new ReviewHintCommand(HintReviewStatus.APPROVED, "A text.", null),
                ReviewHintHandler.HINT_TEXT_WRONG_AUDIENCE);
        assertRefused(
                english,
                new ReviewHintCommand(HintReviewStatus.APPROVED, null, "Un texte."),
                ReviewHintHandler.HINT_TEXT_WRONG_AUDIENCE);
    }

    @Test
    void anUnknownHintIsNotFound() {
        UUID id = UUID.randomUUID();
        when(hints.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(ADMIN, id, decision(HintReviewStatus.APPROVED)))
                .isInstanceOf(NotFoundException.class);
    }
}
