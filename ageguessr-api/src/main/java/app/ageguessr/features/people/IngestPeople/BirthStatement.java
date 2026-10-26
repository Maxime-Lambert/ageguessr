package app.ageguessr.features.people.IngestPeople;

/** One Wikidata "date of birth" (P569) statement, as the selection query returns it. */
record BirthStatement(String time, int precision, String calendarModel, WikidataRank rank) {}
