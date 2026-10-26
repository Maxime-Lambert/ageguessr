package app.ageguessr.features.people.IngestPeople;

import java.util.List;
import java.util.Set;

/**
 * One person as the QLever selection query describes them, its rows grouped. Dates are
 * still raw statements: {@link BirthDateSelector} and {@link DeathDateSelector} decide
 * what can be trusted.
 */
record PersonRecord(
        String wikidataId,
        int sitelinks,
        String nameEn,
        String nameFr,
        Set<String> mainImageFiles,
        List<BirthStatement> births,
        List<DeathStatement> deaths) {}
