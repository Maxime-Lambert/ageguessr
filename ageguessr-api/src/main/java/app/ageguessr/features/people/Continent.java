package app.ageguessr.features.people;

/**
 * Continent of a person's nationality (Wikidata P27, then the country's P30), used only
 * to balance the pool between origins. Not ethnicity (P172): stated for ~1% of people,
 * and a special category of data under GDPR art. 9. Nationality is not origin, a known
 * limit (plans/active-plan.md).
 */
public enum Continent {
    AFRICA,
    ASIA,
    EUROPE,
    NORTH_AMERICA,
    OCEANIA,
    SOUTH_AMERICA
}
