package app.ageguessr.features.people.IngestPeople;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses which accepted photos of one person to keep. At most one photo per calendar
 * year and one per age: two shots at the same age add nothing to the game. Both rules
 * are needed, since a year can span two ages (before and after the birthday) and an age
 * two years. The database enforces the same rules (V4__create_people_tables.sql).
 */
final class PhotoSelector {

    private PhotoSelector() {}

    /** A photo that passed {@link PhotoEvaluator}. {@code mainImage} is the person's Wikidata P18. */
    record Candidate(String fileTitle, boolean mainImage, PhotoEvaluation.Accepted evaluation) {

        int year() {
            return evaluation.date().from().getYear();
        }

        int age() {
            return evaluation.age();
        }

        long intervalDays() {
            return ChronoUnit.DAYS.between(evaluation.date().from(), evaluation.date().to());
        }
    }

    /** A photo already stored for this person, from a previous ingestion run. */
    record Kept(int year, int age) {}

    // Most precise date first, then the photo Wikidata itself shows, then the file name
    // so that two runs over the same candidates keep the same photos.
    private static final Comparator<Candidate> PREFERENCE = Comparator.comparingLong(Candidate::intervalDays)
            .thenComparing(Candidate::mainImage, Comparator.reverseOrder())
            .thenComparing(Candidate::fileTitle);

    static List<Candidate> select(Collection<Kept> alreadyKept, Collection<Candidate> candidates, int maxPerPerson) {
        Set<Integer> years = new HashSet<>();
        Set<Integer> ages = new HashSet<>();
        for (Kept kept : alreadyKept) {
            years.add(kept.year());
            ages.add(kept.age());
        }
        int room = maxPerPerson - alreadyKept.size();
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates.stream().sorted(PREFERENCE).toList()) {
            if (selected.size() >= room) {
                break;
            }
            if (!years.contains(candidate.year()) && !ages.contains(candidate.age())) {
                selected.add(candidate);
                years.add(candidate.year());
                ages.add(candidate.age());
            }
        }
        return selected;
    }
}
