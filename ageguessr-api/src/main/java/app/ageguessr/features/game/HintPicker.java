package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        return new HintOffer(hints.findEligible(gameDate.getYear() - age, audiences(language), personQid));
    }

    /**
     * The hints a player was shown, in their order, without those rejected or deleted since.
     * One query for the round rather than one per hint.
     */
    public List<GameHint> shown(List<UUID> hintIds, Language language) {
        List<UUID> known = hintIds.stream().filter(Objects::nonNull).toList();
        if (known.isEmpty()) {
            return List.of();
        }
        Map<UUID, Hint> found = new HashMap<>();
        hints.findAllById(known).forEach(hint -> found.put(hint.getId(), hint));
        return known.stream()
                .map(found::get)
                .filter(hint -> hint != null && hint.getReviewStatus() == HintReviewStatus.APPROVED)
                .map(hint -> GameHint.of(hint, language))
                .toList();
    }

    /** The hints a player of this language can read: those for everyone, and their own. */
    public static List<HintAudience> audiences(Language language) {
        return List.of(HintAudience.ALL, language == Language.FR ? HintAudience.FR : HintAudience.EN);
    }
}
