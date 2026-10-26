package app.ageguessr.features.people.IngestPeople;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses which accepted photos of one person to keep. Any two of them must be at least
 * {@code minYearsApart} apart both in age and in calendar year: two shots of the same
 * person a few years apart make nearly the same question. Both measures are needed,
 * since a year can span two ages (before and after the birthday) and an age two years.
 * The database only enforces the one-year floor (unique age and unique year per person,
 * V4__create_people_tables.sql): the gap itself is a product setting.
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

    static List<Candidate> select(
            Collection<Kept> alreadyKept, Collection<Candidate> candidates, int maxPerPerson, int minYearsApart) {
        if (minYearsApart < 1) {
            throw new IllegalArgumentException("minYearsApart must be at least 1, got " + minYearsApart);
        }
        List<Kept> taken = new ArrayList<>(alreadyKept);
        int room = maxPerPerson - alreadyKept.size();
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates.stream().sorted(PREFERENCE).toList()) {
            if (selected.size() >= room) {
                break;
            }
            boolean farEnough = taken.stream()
                    .allMatch(k -> Math.abs(k.year() - candidate.year()) >= minYearsApart
                            && Math.abs(k.age() - candidate.age()) >= minYearsApart);
            if (farEnough) {
                selected.add(candidate);
                taken.add(new Kept(candidate.year(), candidate.age()));
            }
        }
        return selected;
    }
}
