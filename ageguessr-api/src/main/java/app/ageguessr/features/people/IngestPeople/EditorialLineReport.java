package app.ageguessr.features.people.IngestPeople;

import java.util.List;
import java.util.Map;

/**
 * Outcome of --apply-editorial-line: the stored people the editorial line catches, how
 * many per criterion (a person can meet several), and the photos their exclusion deletes.
 * {@code dryRun}: nothing was written. The QIDs are printed only for a dry run, for review
 * before applying: in the logs of a real run, a list of QIDs next to "convicted or
 * charged" would read as a list of people tied to crimes.
 */
record EditorialLineReport(
        boolean dryRun,
        List<String> wikidataIds,
        Map<EditorialLine.Criterion, Integer> byCriterion,
        int photosDeleted,
        int approvedPhotosDeleted) {

    EditorialLineReport {
        wikidataIds = List.copyOf(wikidataIds);
        byCriterion = Map.copyOf(byCriterion);
    }

    @Override
    public String toString() {
        return (dryRun ? "dry run, nothing written: " : "") + wikidataIds.size() + " people "
                + (dryRun ? "to exclude" : "excluded") + ", by criterion " + byCriterion + ", photos "
                + (dryRun ? "to delete" : "deleted") + "=" + photosDeleted + " (of which approved="
                + approvedPhotosDeleted + ")" + (dryRun ? ", QIDs=" + wikidataIds : "");
    }
}
