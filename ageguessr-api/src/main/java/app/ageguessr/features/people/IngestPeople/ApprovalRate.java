package app.ageguessr.features.people.IngestPeople;

import java.util.Locale;

/**
 * The share of photos the curation approves, used to count a pending photo as the
 * fraction of an approved one it is worth. Observed once enough photos have been
 * reviewed, assumed (app.people.ingest.expected-approval-rate) before that.
 */
record ApprovalRate(double value, int reviewed, boolean observed) {

    /** Below this many reviewed photos, the observed share is too noisy to trust. */
    static final int MIN_REVIEWED = 300;

    static ApprovalRate of(ReviewCounts all, double assumed) {
        int reviewed = all.approved() + all.rejected();
        return reviewed >= MIN_REVIEWED
                ? new ApprovalRate((double) all.approved() / reviewed, reviewed, true)
                : new ApprovalRate(assumed, reviewed, false);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.2f", value)
                + (observed ? " observed on " + reviewed + " reviewed photos"
                        : " assumed (" + reviewed + " reviewed photos, " + MIN_REVIEWED + " needed to observe it)");
    }
}
