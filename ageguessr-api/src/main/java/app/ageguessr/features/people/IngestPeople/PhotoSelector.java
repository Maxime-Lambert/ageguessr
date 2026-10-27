package app.ageguessr.features.people.IngestPeople;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * Picks one photo at a time: among the candidates far enough from everything already
     * kept, the one whose age band holds the fewest photos so far ({@code photosByAgeBand},
     * keyed by {@link AgeBand#of}), then by {@code PREFERENCE}. The pool thus leans toward
     * the ages it lacks (74 and over, measured on 2026-10-27) without losing any photo
     * the gap rules would have allowed.
     */
    static List<Candidate> select(
            Collection<Kept> alreadyKept,
            Collection<Candidate> candidates,
            int maxPerPerson,
            int minYearsApart,
            Map<String, Integer> photosByAgeBand) {
        if (minYearsApart < 1) {
            throw new IllegalArgumentException("minYearsApart must be at least 1, got " + minYearsApart);
        }
        List<Kept> taken = new ArrayList<>(alreadyKept);
        Map<String, Integer> bands = new HashMap<>(photosByAgeBand);
        List<Candidate> remaining = new ArrayList<>(candidates);
        int room = maxPerPerson - alreadyKept.size();
        List<Candidate> selected = new ArrayList<>();
        while (selected.size() < room) {
            Optional<Candidate> next = remaining.stream()
                    .filter(candidate -> taken.stream()
                            .allMatch(k -> Math.abs(k.year() - candidate.year()) >= minYearsApart
                                    && Math.abs(k.age() - candidate.age()) >= minYearsApart))
                    .min(Comparator.comparingInt((Candidate c) -> bands.getOrDefault(AgeBand.of(c.age()), 0))
                            .thenComparing(PREFERENCE));
            if (next.isEmpty()) {
                break;
            }
            Candidate candidate = next.get();
            selected.add(candidate);
            taken.add(new Kept(candidate.year(), candidate.age()));
            bands.merge(AgeBand.of(candidate.age()), 1, Integer::sum);
            remaining.remove(candidate);
        }
        return selected;
    }
}
