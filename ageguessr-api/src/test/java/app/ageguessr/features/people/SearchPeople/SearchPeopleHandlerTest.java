package app.ageguessr.features.people.SearchPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchPeopleHandlerTest {

    private final PersonRepository people = mock(PersonRepository.class);
    private final PersonPhotoRepository photos = mock(PersonPhotoRepository.class);
    private final SearchPeopleHandler handler = new SearchPeopleHandler(people, photos);

    @Test
    void aQidIsLookedUpExactlyWhateverItsCase() {
        Person person = person("Q42");
        when(people.findByWikidataId("Q42")).thenReturn(Optional.of(person));

        SearchPeopleResponse response = handler.handle(new SearchPeopleQuery(" q42 "));

        assertThat(response.people()).extracting(SearchPeopleResponse.Result::wikidataId).containsExactly("Q42");
        verify(people, never()).searchByName(anyString(), anyInt());
    }

    @Test
    void anUnknownQidFindsNobody() {
        when(people.findByWikidataId("Q42")).thenReturn(Optional.empty());

        assertThat(handler.handle(new SearchPeopleQuery("Q42")).people()).isEmpty();
    }

    @Test
    void anythingElseIsANameSearchWithWildcardsEscaped() {
        when(people.searchByName("50\\% Off\\_", SearchPeopleHandler.MAX_RESULTS)).thenReturn(List.of());

        handler.handle(new SearchPeopleQuery("50% Off_"));

        verify(people).searchByName("50\\% Off\\_", 20);
    }

    @Test
    void reportsHowManyPhotosEachPersonHasAndWhetherTheyAreExcluded() {
        Person withPhotos = person("Q1");
        Person excluded = person("Q2");
        excluded.setExcluded(true);
        when(people.searchByName("Jane", 20)).thenReturn(List.of(withPhotos, excluded));
        when(photos.findByPersonIdIn(List.of(withPhotos.getId(), excluded.getId())))
                .thenReturn(List.of(photoOf(withPhotos), photoOf(withPhotos)));

        SearchPeopleResponse response = handler.handle(new SearchPeopleQuery("Jane"));

        assertThat(response.people())
                .extracting(SearchPeopleResponse.Result::photoCount, SearchPeopleResponse.Result::excluded)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, false), org.assertj.core.groups.Tuple.tuple(0, true));
    }

    @Test
    void escapesTheBackslashFirst() {
        assertThat(SearchPeopleHandler.escapeLikeWildcards("a\\%b")).isEqualTo("a\\\\\\%b");
    }

    private static Person person(String qid) {
        return Person.builder()
                .id(UUID.randomUUID())
                .wikidataId(qid)
                .nameEn("Jane Doe")
                .birthDate(LocalDate.of(1950, 3, 10))
                .build();
    }

    private static PersonPhoto photoOf(Person person) {
        return PersonPhoto.builder().id(UUID.randomUUID()).personId(person.getId()).build();
    }
}
