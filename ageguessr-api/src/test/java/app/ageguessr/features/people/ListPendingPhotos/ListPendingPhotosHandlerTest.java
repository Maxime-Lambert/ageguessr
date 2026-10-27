package app.ageguessr.features.people.ListPendingPhotos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.features.people.PendingPhoto;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.ReviewStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class ListPendingPhotosHandlerTest {

    private final PersonPhotoRepository photos = mock(PersonPhotoRepository.class);

    @Test
    void describesEachPendingPhotoWithItsPersonAndSiblings() {
        Person person = person(LocalDate.of(2010, 1, 1));
        PersonPhoto pending = photo(person, 1990, ReviewStatus.PENDING);
        PersonPhoto later = photo(person, 2005, ReviewStatus.APPROVED);
        PersonPhoto earlier = photo(person, 1970, ReviewStatus.REJECTED);
        when(photos.findPending(any(Pageable.class))).thenReturn(List.of(new PendingPhoto(pending, person)));
        when(photos.findByPersonIdIn(List.of(person.getId()))).thenReturn(List.of(later, pending, earlier));
        when(photos.countPending()).thenReturn(42L);

        PendingPhotosResponse response = handler().handle(new ListPendingPhotosQuery(20));

        assertThat(response.totalPending()).isEqualTo(42);
        PendingPhotosResponse.Item item = response.items().getFirst();
        assertThat(item.photoId()).isEqualTo(pending.getId());
        assertThat(item.person().wikidataId()).isEqualTo("Q1");
        assertThat(item.person().deathDate()).isEqualTo(LocalDate.of(2010, 1, 1));
        assertThat(item.ageAtPhoto()).isEqualTo(40);
        assertThat(item.commonsFile()).isEqualTo("Photo 1990.jpg");
        // The photo itself is not among its siblings, which come oldest first.
        assertThat(item.otherPhotos())
                .extracting(PendingPhotosResponse.OtherPhoto::photoId)
                .containsExactly(earlier.getId(), later.getId());
        assertThat(item.otherPhotos())
                .extracting(PendingPhotosResponse.OtherPhoto::reviewStatus)
                .containsExactly(ReviewStatus.REJECTED, ReviewStatus.APPROVED);
    }

    @Test
    void anEmptyQueue() {
        when(photos.findPending(any(Pageable.class))).thenReturn(List.of());

        PendingPhotosResponse response = handler().handle(new ListPendingPhotosQuery(20));

        assertThat(response.items()).isEmpty();
        assertThat(response.totalPending()).isZero();
    }

    @Test
    void asksForTheRequestedBatchSize() {
        when(photos.findPending(any(Pageable.class))).thenAnswer(invocation -> {
            assertThat(((Pageable) invocation.getArgument(0)).getPageSize()).isEqualTo(7);
            return List.of();
        });

        handler().handle(new ListPendingPhotosQuery(7));
    }

    @Test
    void defaultsToABatchOfTwenty() {
        assertThat(new ListPendingPhotosQuery(null).limit()).isEqualTo(20);
    }

    private ListPendingPhotosHandler handler() {
        return new ListPendingPhotosHandler(photos);
    }

    private static Person person(LocalDate death) {
        return Person.builder()
                .id(UUID.randomUUID())
                .wikidataId("Q1")
                .nameEn("Jane Doe")
                .birthDate(LocalDate.of(1950, 3, 10))
                .deathDate(death)
                .sitelinks(42)
                .build();
    }

    private static PersonPhoto photo(Person person, int year, ReviewStatus status) {
        return PersonPhoto.builder()
                .id(UUID.randomUUID())
                .personId(person.getId())
                .commonsFile("Photo " + year + ".jpg")
                .takenFrom(LocalDate.of(year, 7, 14))
                .takenTo(LocalDate.of(year, 7, 14))
                .ageAtPhoto(year - 1950)
                .dateSource(PhotoDateSource.BOTH)
                .reviewStatus(status)
                .build();
    }
}
