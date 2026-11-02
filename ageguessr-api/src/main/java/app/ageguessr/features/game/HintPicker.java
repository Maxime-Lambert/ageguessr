package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads the hints of a round from the hints feature (game → hints, never the reverse). */
@Component
@RequiredArgsConstructor
public class HintPicker {

    private final HintRepository hints;

    /**
     * The hints of the year the photo's age points to: the year of the game minus that
     * age, since the player does not know when the photo was taken (V11).
     */
    public HintOffer offerFor(LocalDate gameDate, int age, Language language, String personQid) {
        return new HintOffer(hints.findEligible(gameDate.getYear() - age, language.audiences(), personQid));
    }

    /** The hint a player was shown, unless it has been rejected or deleted since. */
    public Optional<GameHint> shown(UUID hintId, Language language) {
        if (hintId == null) {
            return Optional.empty();
        }
        return hints.findById(hintId)
                .filter(hint -> hint.getReviewStatus() == HintReviewStatus.APPROVED)
                .map(hint -> GameHint.of(hint, language));
    }
}
