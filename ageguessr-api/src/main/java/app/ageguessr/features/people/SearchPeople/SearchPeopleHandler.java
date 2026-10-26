package app.ageguessr.features.people.SearchPeople;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds the person a removal request is about, by QID or by name. */
@Service
@RequiredArgsConstructor
public class SearchPeopleHandler {

    static final int MAX_RESULTS = 20;
    private static final Pattern QID = Pattern.compile("^Q[1-9][0-9]{0,11}$");

    private final PersonRepository people;
    private final PersonPhotoRepository photos;

    @Transactional(readOnly = true)
    public SearchPeopleResponse handle(SearchPeopleQuery query) {
        String text = query.query().strip();
        String upper = text.toUpperCase(Locale.ROOT);
        List<Person> found = QID.matcher(upper).matches()
                ? people.findByWikidataId(upper).map(List::of).orElse(List.of())
                : people.searchByName(escapeLikeWildcards(text), MAX_RESULTS);
        Map<UUID, Long> photoCounts = photos.findByPersonIdIn(found.stream().map(Person::getId).toList()).stream()
                .collect(Collectors.groupingBy(PersonPhoto::getPersonId, Collectors.counting()));
        return new SearchPeopleResponse(found.stream()
                .map(p -> new SearchPeopleResponse.Result(
                        p.getId(),
                        p.getWikidataId(),
                        p.getNameEn(),
                        p.getNameFr(),
                        p.getBirthDate(),
                        p.getDeathDate(),
                        p.isExcluded(),
                        photoCounts.getOrDefault(p.getId(), 0L).intValue()))
                .toList());
    }

    /** "50%" must look for a literal percent sign, not match everything. */
    static String escapeLikeWildcards(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
