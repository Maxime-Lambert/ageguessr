package app.ageguessr.features.people.IngestPeople;

/**
 * What to ingest. {@code wikidataId} set: that one person only. Otherwise people by
 * notoriety from rank {@code offset} until {@code targetPeople} of them have at least one
 * photo, or until {@code sample} people have been looked at (0: no such cap).
 * {@code dryRun}: every rule runs, downloads and re-encoding included, but nothing is
 * read from or written to the database. Together, offset, sample and dry run measure the
 * yield of one part of the list.
 */
record IngestPeopleCommand(int targetPeople, String wikidataId, int offset, int sample, boolean dryRun) {}
