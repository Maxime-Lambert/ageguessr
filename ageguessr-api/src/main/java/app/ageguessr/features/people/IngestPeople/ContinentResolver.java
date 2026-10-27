package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Continent;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns the continents of a person's nationalities (P27, then the country's P30) into one
 * {@link Continent}. The mapping covers every P30 value found on nationality countries
 * (QLever, 2026-10-27); the ones that span several continents ("Americas",
 * "Afro-Eurasia") say nothing and are ignored.
 */
final class ContinentResolver {

    private static final Map<String, Continent> KNOWN = Map.ofEntries(
            Map.entry("Q15", Continent.AFRICA),
            Map.entry("Q48", Continent.ASIA),
            Map.entry("Q5401", Continent.ASIA), // Eurasia
            Map.entry("Q60140", Continent.ASIA), // Indian subcontinent
            Map.entry("Q46", Continent.EUROPE),
            Map.entry("Q49", Continent.NORTH_AMERICA),
            Map.entry("Q27611", Continent.NORTH_AMERICA), // Central America
            Map.entry("Q19464773", Continent.NORTH_AMERICA), // Northern America and Mexico
            Map.entry("Q18", Continent.SOUTH_AMERICA),
            Map.entry("Q538", Continent.OCEANIA), // Insular Oceania
            Map.entry("Q55643", Continent.OCEANIA),
            Map.entry("Q3960", Continent.OCEANIA)); // Australian continent

    /**
     * Least represented first among people with a P18 image born since 1930
     * (plans/done/people-pool-balance.md): someone with nationalities on several
     * continents counts where they help the balance most, and always the same way.
     */
    private static final List<Continent> PRIORITY = List.of(
            Continent.OCEANIA,
            Continent.AFRICA,
            Continent.SOUTH_AMERICA,
            Continent.ASIA,
            Continent.NORTH_AMERICA,
            Continent.EUROPE);

    private ContinentResolver() {}

    /** The P30 values that fold into {@code continent}. */
    static Set<String> qidsOf(Continent continent) {
        return KNOWN.entrySet().stream()
                .filter(e -> e.getValue() == continent)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Empty if no nationality leads to a known continent. */
    static Optional<Continent> resolve(Set<String> continentQids) {
        Set<Continent> continents = continentQids.stream()
                .map(KNOWN::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return PRIORITY.stream().filter(continents::contains).findFirst();
    }
}
