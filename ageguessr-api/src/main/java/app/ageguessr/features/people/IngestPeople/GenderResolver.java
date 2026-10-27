package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Gender;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns a person's P21 values into one {@link Gender}. The values come from wdt:P21, which
 * already keeps only the best-ranked statements (preferred if any, else normal, never
 * deprecated), so a corrected gender wins over the old one.
 */
final class GenderResolver {

    private static final Map<String, Gender> KNOWN = Map.of(
            "Q6581097", Gender.MALE, // male
            "Q15145778", Gender.MALE, // cisgender male
            "Q2449503", Gender.MALE, // trans man
            "Q6581072", Gender.FEMALE, // female
            "Q15145779", Gender.FEMALE, // cisgender female
            "Q1052281", Gender.FEMALE); // trans woman

    private GenderResolver() {}

    /** Empty if Wikidata states no gender. Several values that disagree give OTHER. */
    static Optional<Gender> resolve(Set<String> genderQids) {
        Set<Gender> genders = genderQids.stream()
                .map(qid -> KNOWN.getOrDefault(qid, Gender.OTHER))
                .collect(Collectors.toSet());
        if (genders.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(genders.size() == 1 ? genders.iterator().next() : Gender.OTHER);
    }
}
