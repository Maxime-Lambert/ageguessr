package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.Continent;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContinentResolverTest {

    @Test
    void readsEachContinent() {
        assertThat(ContinentResolver.resolve(Set.of("Q15"))).contains(Continent.AFRICA);
        assertThat(ContinentResolver.resolve(Set.of("Q48"))).contains(Continent.ASIA);
        assertThat(ContinentResolver.resolve(Set.of("Q46"))).contains(Continent.EUROPE);
        assertThat(ContinentResolver.resolve(Set.of("Q49"))).contains(Continent.NORTH_AMERICA);
        assertThat(ContinentResolver.resolve(Set.of("Q18"))).contains(Continent.SOUTH_AMERICA);
        assertThat(ContinentResolver.resolve(Set.of("Q538"))).contains(Continent.OCEANIA);
    }

    @Test
    void foldsTheRegionsWikidataUsesIntoTheSixContinents() {
        assertThat(ContinentResolver.resolve(Set.of("Q5401"))).as("Eurasia").contains(Continent.ASIA);
        assertThat(ContinentResolver.resolve(Set.of("Q60140"))).as("Indian subcontinent").contains(Continent.ASIA);
        assertThat(ContinentResolver.resolve(Set.of("Q27611"))).as("Central America").contains(Continent.NORTH_AMERICA);
        assertThat(ContinentResolver.resolve(Set.of("Q19464773")))
                .as("Northern America and Mexico")
                .contains(Continent.NORTH_AMERICA);
        assertThat(ContinentResolver.resolve(Set.of("Q55643"))).as("Oceania").contains(Continent.OCEANIA);
        assertThat(ContinentResolver.resolve(Set.of("Q3960"))).as("Australian continent").contains(Continent.OCEANIA);
    }

    @Test
    void severalContinentsGiveTheLeastRepresentedOne() {
        // Russia: Europe and Asia.
        assertThat(ContinentResolver.resolve(Set.of("Q46", "Q48"))).contains(Continent.ASIA);
        assertThat(ContinentResolver.resolve(Set.of("Q49", "Q18"))).contains(Continent.SOUTH_AMERICA);
        assertThat(ContinentResolver.resolve(Set.of("Q46", "Q15", "Q538"))).contains(Continent.OCEANIA);
        assertThat(ContinentResolver.resolve(Set.of("Q46", "Q49"))).contains(Continent.NORTH_AMERICA);
        assertThat(ContinentResolver.resolve(Set.of("Q15", "Q48"))).contains(Continent.AFRICA);
    }

    @Test
    void ignoresRegionsSpanningSeveralContinents() {
        assertThat(ContinentResolver.resolve(Set.of("Q828"))).as("Americas").isEmpty();
        assertThat(ContinentResolver.resolve(Set.of("Q27527", "Q46"))).as("Afro-Eurasia").contains(Continent.EUROPE);
    }

    @Test
    void noNationalityIsUnknown() {
        assertThat(ContinentResolver.resolve(Set.of())).isEmpty();
    }
}
