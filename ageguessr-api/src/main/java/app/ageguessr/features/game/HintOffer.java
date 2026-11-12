package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintCategory;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
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

    /** How many categories a player picks: {@code perRound}, or all of them when fewer are offered. */
    public int required(int perRound) {
        return Math.min(perRound, categories().size());
    }

    /**
     * The hint of each category chosen, each one drawn as {@link #pick} does, so that two
     * players who chose the same category see the same hint whatever else they chose.
     *
     * @return in the order of {@link HintCategory}, whatever the order of the request, so
     *     that the hints never move between two loads of the round
     * @throws app.ageguessr.shared.exceptions.ValidationException HINT_COUNT when not
     *     exactly {@link #required} distinct categories
     * @throws app.ageguessr.shared.exceptions.ConflictException CATEGORY_UNAVAILABLE when one is not offered
     */
    public List<Hint> pickAll(String roundKey, Collection<HintCategory> chosen, Language language, int perRound) {
        // EnumSet.copyOf refuses an empty collection; @NotEmpty already keeps one out of the handlers.
        EnumSet<HintCategory> distinct = chosen.isEmpty() ? EnumSet.noneOf(HintCategory.class) : EnumSet.copyOf(chosen);
        if (distinct.size() != chosen.size() || chosen.size() != required(perRound)) {
            throw GameErrors.wrongHintCount();
        }
        return distinct.stream()
                .map(category -> pick(roundKey, category, language).orElseThrow(GameErrors::categoryUnavailable))
                .toList();
    }
}
