package app.ageguessr.features.people.ExcludePerson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.people.ExclusionReason;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExcludePersonHandlerTest {

    private final PersonRepository people = mock(PersonRepository.class);
    private final PersonPhotoRepository photos = mock(PersonPhotoRepository.class);
    private final ExcludePersonHandler handler = new ExcludePersonHandler(people, photos);

    @Test
    void marksThePersonExcludedAndReportsThePhotosTheTriggerDeletes() {
        Person person = Person.builder().id(UUID.randomUUID()).wikidataId("Q1").build();
        when(people.findById(person.getId())).thenReturn(Optional.of(person));
        when(photos.findByPersonId(person.getId())).thenReturn(List.of(new PersonPhoto(), new PersonPhoto()));

        ExcludePersonResponse response = handler.handle(new ExcludePersonCommand(person.getId()));

        assertThat(response).isEqualTo(new ExcludePersonResponse(person.getId(), 2));
        assertThat(person.isExcluded()).isTrue();
        assertThat(person.getExclusionReason()).isEqualTo(ExclusionReason.MANUAL);
        verify(people).saveAndFlush(person);
    }

    @Test
    void excludingTwiceChangesNothing() {
        Person person = Person.builder().id(UUID.randomUUID()).excluded(true).build();
        when(people.findById(person.getId())).thenReturn(Optional.of(person));

        assertThat(handler.handle(new ExcludePersonCommand(person.getId())).deletedPhotos()).isZero();
        verify(people, never()).saveAndFlush(any());
    }

    @Test
    void anUnknownPersonIsNotFound() {
        when(people.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new ExcludePersonCommand(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }
}
