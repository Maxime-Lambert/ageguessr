package app.ageguessr.features.people.IngestPeople;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Counters of one ingestion run, logged at the end so a run can be judged at a glance.
 * People are also counted per balance cell ("EUROPE/FEMALE", "NONE/UNKNOWN" when Wikidata
 * does not tell), seen and kept, to measure the yield of each cell. Kept people are also
 * counted per occupation (P106 QID, a person counting once for each), to see which ones
 * crowd the pool.
 */
class IngestReport {

    private static final String NONE = "NONE";
    private static final String UNKNOWN = "UNKNOWN";
    static final int TOP_OCCUPATIONS = 15;

    private int peopleSeen;
    private int peopleKept;
    private int photosAdded;
    private long imageBytes;
    private final Map<String, Integer> peopleSkipped = new TreeMap<>();
    private final Map<String, Integer> photosRejected = new TreeMap<>();
    private final Map<String, Integer> seenByCell = new TreeMap<>();
    private final Map<String, Integer> keptByCell = new TreeMap<>();
    private final Map<String, String> balance = new TreeMap<>();
    private final Map<String, Integer> validPhotosByAge = new TreeMap<>();
    private final Map<String, Integer> keptPhotosByAge = new TreeMap<>();
    private final Map<String, Integer> keptByOccupation = new HashMap<>();

    void personSeen(PersonRecord person) {
        peopleSeen++;
        seenByCell.merge(cell(person), 1, Integer::sum);
    }

    void personKept(PersonRecord person, List<NewPhoto> newPhotos) {
        peopleKept++;
        keptByCell.merge(cell(person), 1, Integer::sum);
        person.occupations().forEach(occupation -> keptByOccupation.merge(occupation, 1, Integer::sum));
        photosAdded += newPhotos.size();
        imageBytes += newPhotos.stream().mapToLong(p -> p.image().content().length).sum();
    }

    void personSkipped(PersonSkip reason) {
        peopleSkipped.merge(reason.name(), 1, Integer::sum);
    }

    void photoRejected(String reason) {
        photosRejected.merge(reason, 1, Integer::sum);
    }

    /** A photo whose date gives an exact age, before the per-person selection. */
    void validPhoto(int age) {
        validPhotosByAge.merge(AgeBand.of(age), 1, Integer::sum);
    }

    void photoKept(int age) {
        keptPhotosByAge.merge(AgeBand.of(age), 1, Integer::sum);
    }

    Map<String, Integer> validPhotosByAge() {
        return Map.copyOf(validPhotosByAge);
    }

    Map<String, Integer> keptPhotosByAge() {
        return Map.copyOf(keptPhotosByAge);
    }

    void photosNotSelected(int count) {
        if (count > 0) {
            photosRejected.merge("NOT_SELECTED", count, Integer::sum);
        }
    }

    int peopleSeen() {
        return peopleSeen;
    }

    int peopleKept() {
        return peopleKept;
    }

    int photosAdded() {
        return photosAdded;
    }

    long imageBytes() {
        return imageBytes;
    }

    Map<String, Integer> peopleSkipped() {
        return Map.copyOf(peopleSkipped);
    }

    Map<String, Integer> photosRejected() {
        return Map.copyOf(photosRejected);
    }

    Map<String, Integer> seenByCell() {
        return Map.copyOf(seenByCell);
    }

    Map<String, Integer> keptByCell() {
        return Map.copyOf(keptByCell);
    }

    void cellStatus(Cell cell, int photos, int quota, boolean exhausted) {
        balance.put(cell.toString(), photos + "/" + quota + (exhausted ? " exhausted" : ""));
    }

    /** The most frequent occupations among kept people, most frequent first (QID on ties). */
    Map<String, Integer> topOccupations() {
        Map<String, Integer> top = new LinkedHashMap<>();
        keptByOccupation.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_OCCUPATIONS)
                .forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        return top;
    }

    Map<String, String> balance() {
        return Map.copyOf(balance);
    }

    private static String cell(PersonRecord person) {
        return (person.continent() == null ? NONE : person.continent().name()) + "/"
                + (person.gender() == null ? UNKNOWN : person.gender().name());
    }

    @Override
    public String toString() {
        String photosPerPerson = peopleKept == 0 ? "-" : String.format(Locale.ROOT, "%.2f", (double) photosAdded / peopleKept);
        String kbPerImage = photosAdded == 0 ? "-" : String.valueOf(imageBytes / 1024 / photosAdded);
        return "people seen=" + peopleSeen + ", kept=" + peopleKept + ", photos added=" + photosAdded
                + ", photos per kept person=" + photosPerPerson + ", KB per image=" + kbPerImage
                + ", people skipped=" + peopleSkipped + ", photos rejected=" + photosRejected
                + ", seen by cell=" + seenByCell + ", kept by cell=" + keptByCell
                + ", valid photos by age=" + validPhotosByAge + ", kept photos by age=" + keptPhotosByAge
                + ", kept people by occupation (top " + TOP_OCCUPATIONS + ")=" + topOccupations()
                + (balance.isEmpty() ? "" : ", balance (photos/quota)=" + balance);
    }
}
