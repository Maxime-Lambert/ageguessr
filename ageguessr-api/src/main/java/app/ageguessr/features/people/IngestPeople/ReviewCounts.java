package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.ReviewStatus;

/** Photos of one part of the pool (a cell, an age band) per review status. */
record ReviewCounts(int approved, int pending, int rejected) {

    static final ReviewCounts NONE = new ReviewCounts(0, 0, 0);

    static ReviewCounts of(ReviewStatus status, int photos) {
        return switch (status) {
            case APPROVED -> new ReviewCounts(photos, 0, 0);
            case PENDING -> new ReviewCounts(0, photos, 0);
            case REJECTED -> new ReviewCounts(0, 0, photos);
        };
    }

    ReviewCounts plus(ReviewCounts other) {
        return new ReviewCounts(approved + other.approved, pending + other.pending, rejected + other.rejected);
    }

    /** Pending or approved: not rejected. */
    int inPlay() {
        return approved + pending;
    }

    /** The approved photos to expect once the pending ones are reviewed. */
    double expected(ApprovalRate rate) {
        return approved + pending * rate.value();
    }
}
