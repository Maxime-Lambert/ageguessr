package app.ageguessr.features.people.IngestPeople;

/**
 * What to ingest. {@code wikidataId} set: that one person only. Otherwise until
 * {@code targetPhotos} photos are in play (pending or approved), shared between the
 * balance cells; or, with {@code sample} > 0, the plain ranking from rank {@code offset}
 * until that many people have been looked at, to measure the yield. {@code dryRun}:
 * every rule runs, downloads and re-encoding included, but nothing is read from or
 * written to the database. {@code focus}: what this wave aims at, {@link IngestFocus#NONE}
 * for everything.
 */
record IngestPeopleCommand(
        int targetPhotos, String wikidataId, int offset, int sample, boolean dryRun, IngestFocus focus) {}
