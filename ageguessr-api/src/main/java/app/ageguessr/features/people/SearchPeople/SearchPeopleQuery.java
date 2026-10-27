package app.ageguessr.features.people.SearchPeople;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A name fragment, or an exact Wikidata QID ("Q42"), to find the person a removal request is about. */
public record SearchPeopleQuery(@NotBlank @Size(min = 2, max = 100) String query) {}
