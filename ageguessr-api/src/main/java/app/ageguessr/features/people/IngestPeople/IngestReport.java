package app.ageguessr.features.people.IngestPeople;

import java.util.Map;
import java.util.TreeMap;

/** Counters of one ingestion run, logged at the end so a run can be judged at a glance. */
class IngestReport {

    private int peopleSeen;
    private int peopleKept;
    private int photosAdded;
    private final Map<String, Integer> peopleSkipped = new TreeMap<>();
    private final Map<String, Integer> photosRejected = new TreeMap<>();

    void personSeen() {
        peopleSeen++;
    }

    void personKept(int newPhotos) {
        peopleKept++;
        photosAdded += newPhotos;
    }

    void personSkipped(PersonSkip reason) {
        peopleSkipped.merge(reason.name(), 1, Integer::sum);
    }

    void photoRejected(String reason) {
        photosRejected.merge(reason, 1, Integer::sum);
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

    Map<String, Integer> peopleSkipped() {
        return Map.copyOf(peopleSkipped);
    }

    Map<String, Integer> photosRejected() {
        return Map.copyOf(photosRejected);
    }

    @Override
    public String toString() {
        return "people seen=" + peopleSeen + ", kept=" + peopleKept + ", photos added=" + photosAdded
                + ", people skipped=" + peopleSkipped + ", photos rejected=" + photosRejected;
    }
}
