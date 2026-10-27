package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.PhotoDateSource;

/** Outcome of checking a candidate photo's date against the person's life. */
sealed interface PhotoEvaluation {

    record Accepted(PhotoDate date, PhotoDateSource source, int age) implements PhotoEvaluation {}

    record Rejected(PhotoRejection reason) implements PhotoEvaluation {}
}
