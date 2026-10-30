package app.ageguessr.features.hints.ReviewHint;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintText;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a curation decision, with the text corrected if need be. Every transition is
 * allowed: undo goes back to PENDING, and a hint already APPROVED can be rejected. A
 * corrected text obeys the same rules as an imported one, since it reaches players the
 * same way.
 */
@Service
@RequiredArgsConstructor
public class ReviewHintHandler {

    static final String HINT_TEXT_BLANK = "HINT_TEXT_BLANK";
    static final String HINT_TEXT_CONTAINS_YEAR = "HINT_TEXT_CONTAINS_YEAR";
    static final String HINT_TEXT_WRONG_AUDIENCE = "HINT_TEXT_WRONG_AUDIENCE";

    private final HintRepository hints;
    private final Clock clock;

    @Transactional
    public ReviewHintResponse handle(UUID adminId, UUID hintId, ReviewHintCommand command) {
        Hint hint = hints.findById(hintId).orElseThrow(() -> new NotFoundException("Hint not found"));
        if (command.textEn() != null) {
            check(command.textEn(), hint.getAudience() != HintAudience.FR);
            hint.setTextEn(command.textEn());
        }
        if (command.textFr() != null) {
            check(command.textFr(), hint.getAudience() != HintAudience.EN);
            hint.setTextFr(command.textFr());
        }
        hint.setReviewStatus(command.decision());
        hint.setReviewedAt(clock.instant());
        hint.setReviewedBy(adminId);
        hints.save(hint);
        return new ReviewHintResponse(hint.getId(), hint.getReviewStatus(), hint.getTextEn(), hint.getTextFr());
    }

    private static void check(String text, boolean audienceReadsThisLanguage) {
        if (!audienceReadsThisLanguage) {
            throw new ValidationException(HINT_TEXT_WRONG_AUDIENCE, "This hint has no text in that language");
        }
        if (text.isBlank()) {
            throw new ValidationException(HINT_TEXT_BLANK, "The text cannot be empty");
        }
        if (HintText.containsAYear(text)) {
            throw new ValidationException(HINT_TEXT_CONTAINS_YEAR, "The text cannot contain a year");
        }
    }
}
