package app.ageguessr.features.people.IngestPeople;

/**
 * What to ingest. {@code wikidataId} set: that one person only. Otherwise people by
 * notoriety until {@code targetPeople} of them have at least one photo.
 */
record IngestPeopleCommand(int targetPeople, String wikidataId) {}
