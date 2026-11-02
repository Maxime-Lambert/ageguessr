package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintCategory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HintOfferTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Test
    void offersOnlyTheCategoriesWithEnoughHintsInTheirFixedOrder() {
        List<Hint> eligible = new ArrayList<>();
        eligible.addAll(hints(HintCategory.WORLD, 5));
        eligible.addAll(hints(HintCategory.MUSIC, 7));
        eligible.addAll(hints(HintCategory.FILM, 4));

        HintOffer offer = new HintOffer(eligible);

        assertThat(offer.categories()).containsExactly(HintCategory.MUSIC, HintCategory.WORLD);
        assertThat(offer.offers(HintCategory.FILM)).isFalse();
        assertThat(offer.offers(HintCategory.SPORT)).isFalse();
    }

    @Test
    void offersNothingWithoutHints() {
        HintOffer offer = new HintOffer(List.of());

        assertThat(offer.categories()).isEmpty();
        assertThat(offer.pick(DAY, (short) 1, HintCategory.MUSIC, Language.EN)).isEmpty();
    }

    @Test
    void picksNothingInACategoryUnderTheMinimum() {
        HintOffer offer = new HintOffer(hints(HintCategory.FILM, 4));

        assertThat(offer.pick(DAY, (short) 1, HintCategory.FILM, Language.EN)).isEmpty();
    }

    @Test
    void picksTheSameHintForTheSameRoundCategoryAndLanguage() {
        List<Hint> music = hints(HintCategory.MUSIC, 9);

        Hint first = new HintOffer(music).pick(DAY, (short) 2, HintCategory.MUSIC, Language.FR).orElseThrow();
        Hint again = new HintOffer(List.copyOf(music)).pick(DAY, (short) 2, HintCategory.MUSIC, Language.FR).orElseThrow();

        assertThat(again).isSameAs(first);
        assertThat(music).contains(first);
    }

    @Test
    void spreadsThePicksAcrossTheHintsOfACategory() {
        List<Hint> music = hints(HintCategory.MUSIC, 6);
        HintOffer offer = new HintOffer(music);
        Set<Hint> picked = new HashSet<>();

        for (int day = 0; day < 60; day++) {
            for (Language language : Language.values()) {
                picked.add(offer.pick(DAY.plusDays(day), (short) 1, HintCategory.MUSIC, language).orElseThrow());
            }
        }

        assertThat(picked).as("a fixed index would always show the same hint").hasSizeGreaterThan(3);
    }

    private static List<Hint> hints(HintCategory category, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Hint.builder().id(UUID.randomUUID()).category(category).year(1986).build())
                .toList();
    }
}
