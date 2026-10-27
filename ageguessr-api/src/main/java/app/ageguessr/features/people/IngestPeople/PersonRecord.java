package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.List;
import java.util.Set;

/**
 * One person as the QLever selection query describes them, its rows grouped. Dates are
 * still raw statements: {@link BirthDateSelector} and {@link DeathDateSelector} decide
 * what can be trusted. {@code gender} and {@code continent} are null when Wikidata does
 * not tell them.
 */
record PersonRecord(
        String wikidataId,
        int sitelinks,
        String nameEn,
        String nameFr,
        Set<String> mainImageFiles,
        List<BirthStatement> births,
        List<DeathStatement> deaths,
        Gender gender,
        Continent continent) {

    /** QID only: names and file titles (which usually contain the name) never reach a log. */
    @Override
    public String toString() {
        return "PersonRecord[" + wikidataId + "]";
    }
}
