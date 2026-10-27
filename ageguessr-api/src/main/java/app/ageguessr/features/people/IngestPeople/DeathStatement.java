package app.ageguessr.features.people.IngestPeople;

/**
 * One Wikidata "date of death" (P570) statement. {@code time} is null for an "unknown
 * value" statement: the person is known to be dead, but not when.
 */
record DeathStatement(String time, Integer precision, WikidataRank rank) {}
