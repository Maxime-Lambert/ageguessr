package app.ageguessr.features.people.SearchPeople;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SearchPeopleResponse(List<Result> people) {

    public record Result(
            UUID id,
            String wikidataId,
            String nameEn,
            String nameFr,
            LocalDate birthDate,
            LocalDate deathDate,
            boolean excluded,
            int photoCount) {}
}
