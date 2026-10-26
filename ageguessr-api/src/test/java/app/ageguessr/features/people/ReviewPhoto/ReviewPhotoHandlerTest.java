package app.ageguessr.features.people.ReviewPhoto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.ReviewStatus;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ReviewPhotoHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();

    private final PersonPhotoRepository photos = mock(PersonPhotoRepository.class);
    private final PersonRepository people = mock(PersonRepository.class);

    @Test
    void approvesAPhotoAndRecordsWhoAndWhen() {
        PersonPhoto photo = stored(person(LocalDate.of(1990, 1, 1), false));

        ReviewPhotoResponse response = handler().handle(ADMIN, photo.getId(), new ReviewPhotoCommand(ReviewStatus.APPROVED));

        assertThat(response).isEqualTo(new ReviewPhotoResponse(photo.getId(), ReviewStatus.APPROVED));
        assertThat(photo.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(photo.getReviewedAt()).isEqualTo(NOW);
        assertThat(photo.getReviewedBy()).isEqualTo(ADMIN);
        verify(photos).saveAndFlush(photo);
    }

    @Test
    void approvesAPhotoOfALivingPerson() {
        PersonPhoto photo = stored(person(null, false));

        handler().handle(ADMIN, photo.getId(), new ReviewPhotoCommand(ReviewStatus.APPROVED));

        assertThat(photo.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void rejectsAPhoto() {
        PersonPhoto photo = stored(person(null, false));

        handler().handle(ADMIN, photo.getId(), new ReviewPhotoCommand(ReviewStatus.REJECTED));

        assertThat(photo.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
    }

    @Test
    void undoesADecisionByGoingBackToPending() {
        PersonPhoto photo = stored(person(null, false));
        photo.setReviewStatus(ReviewStatus.REJECTED);

        handler().handle(ADMIN, photo.getId(), new ReviewPhotoCommand(ReviewStatus.PENDING));

        assertThat(photo.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void turnsATakenAgeOrYearIntoAConflict() {
        PersonPhoto photo = stored(person(LocalDate.of(1990, 1, 1), false));
        photo.setReviewStatus(ReviewStatus.REJECTED);
        when(photos.saveAndFlush(photo)).thenThrow(new DataIntegrityViolationException("person_photos_person_age_unique_idx"));

        assertThatThrownBy(() -> handler().handle(ADMIN, photo.getId(), new ReviewPhotoCommand(ReviewStatus.PENDING)))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo(ReviewPhotoHandler.PHOTO_SLOT_TAKEN);
    }

    @Test
    void anUnknownPhotoIsNotFound() {
        when(photos.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().handle(ADMIN, UUID.randomUUID(), new ReviewPhotoCommand(ReviewStatus.APPROVED)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aPhotoOfAnExcludedPersonIsNotFound() {
        PersonPhoto photo = stored(person(LocalDate.of(1990, 1, 1), true));

        assertThatThrownBy(() -> handler().handle(ADMIN, photo.getId(), new ReviewPhotoCommand(ReviewStatus.APPROVED)))
                .isInstanceOf(NotFoundException.class);
        verify(photos, never()).saveAndFlush(any());
    }

    private ReviewPhotoHandler handler() {
        return new ReviewPhotoHandler(photos, people, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Person person(LocalDate death, boolean excluded) {
        Person person = Person.builder()
                .id(UUID.randomUUID())
                .birthDate(LocalDate.of(1950, 3, 10))
                .deathDate(death)
                .excluded(excluded)
                .build();
        when(people.findById(person.getId())).thenReturn(Optional.of(person));
        return person;
    }

    private PersonPhoto stored(Person person) {
        PersonPhoto photo = PersonPhoto.builder()
                .id(UUID.randomUUID())
                .personId(person.getId())
                .reviewStatus(ReviewStatus.PENDING)
                .build();
        when(photos.findById(photo.getId())).thenReturn(Optional.of(photo));
        return photo;
    }
}
