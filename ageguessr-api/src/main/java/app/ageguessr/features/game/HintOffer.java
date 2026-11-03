package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintCategory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The hints a round can show a player, by category. A category is offered only with at
 * least {@value Hint#MINIMUM_PER_CATEGORY} hints. A hidden category tells the player the
 * year is outside its coverage (no celebrity before 1932, so over 94): a marginal leak,
 * accepted because a category with too few hints would show the same hint too often.
 */
public final class HintOffer {

    private final Map<HintCategory, List<Hint>> byCategory;

    /** {@code eligible} sorted by id, as {@code HintRepository.findEligible} returns them. */
    HintOffer(List<Hint> eligible) {
        this.byCategory = eligible.stream().collect(Collectors.groupingBy(Hint::getCategory));
    }

    /** In the order of {@link HintCategory}, so the buttons never move from day to day. */
    public List<HintCategory> categories() {
        return Arrays.stream(HintCategory.values()).filter(this::offers).toList();
    }

    public boolean offers(HintCategory category) {
        return byCategory.getOrDefault(category, List.of()).size() >= Hint.MINIMUM_PER_CATEGORY;
    }

    /**
     * The same hint for everyone who asks this category in this round and language: the
     * index comes from a hash of the four (day or series, position, category, language).
     * String.hashCode is defined by the language spec, so it does not change across
     * restarts, unlike the hash of an enum.
     *
     * @param roundKey {@link GameRound#hintKey()}: the day and position, or the series and position
     */
    public Optional<Hint> pick(String roundKey, HintCategory category, Language language) {
        if (!offers(category)) {
            return Optional.empty();
        }
        List<Hint> hints = byCategory.get(category);
        String key = roundKey + "|" + category.name() + "|" + language.name();
        return Optional.of(hints.get(Math.floorMod(key.hashCode(), hints.size())));
    }
}
