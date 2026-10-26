package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.features.people.ReviewStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * PeopleStore against a real Postgres. In the feature's test package rather than
 * integration/ because PeopleStore is package-private to the use case. Not
 * @Transactional: PeopleStore opens its own transactions, which is what is tested.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PeopleStoreIntegrationTest {

    private static final LocalDate BIRTH = LocalDate.of(1950, 3, 10);

    @Autowired
    private PeopleStore store;

    @Autowired
    private PersonRepository people;

    @Autowired
    private PersonPhotoRepository photos;

    @Autowired
    private PhotoImageRepository images;

    @AfterEach
    void cleanUp() {
        people.deleteAll();
    }

    @Test
    void storesANewPersonWithPhotosAndImages() {
        store.save(record("Q1", "Jane", 50), BIRTH, null, List.of(photo("A.jpg", 1976)));

        Person person = people.findByWikidataId("Q1").orElseThrow();
        assertThat(person.getNameEn()).isEqualTo("Jane");
        assertThat(person.getBirthDate()).isEqualTo(BIRTH);
        assertThat(person.getCreatedAt()).isNotNull();
        List<PersonPhoto> stored = photos.findByPersonId(person.getId());
        assertThat(stored).singleElement().satisfies(p -> {
            assertThat(p.getCommonsFile()).isEqualTo("A.jpg");
            assertThat(p.getAgeAtPhoto()).isEqualTo(26);
            assertThat(p.getTakenFrom()).isEqualTo(LocalDate.of(1976, 7, 14));
            assertThat(p.getPublicId()).isNotNull().isNotEqualTo(p.getId());
            assertThat(p.getAuthorText()).isEqualTo("Someone");
            assertThat(p.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
        });
        assertThat(images.findById(stored.getFirst().getId()))
                .get()
                .satisfies(i -> {
                    assertThat(i.getContent()).containsExactly(1, 2, 3);
                    assertThat(i.getContentType()).isEqualTo("image/jpeg");
                });
    }

    @Test
    void aSecondRunRefreshesThePersonAndAddsPhotosWithoutDuplicating() {
        store.save(record("Q1", "Jane", 50), BIRTH, null, List.of(photo("A.jpg", 1976)));
        Person first = people.findByWikidataId("Q1").orElseThrow();

        store.save(record("Q1", "Jane Doe", 60), BIRTH, LocalDate.of(2020, 1, 1), List.of(photo("B.jpg", 1990)));

        assertThat(people.findAll()).hasSize(1);
        Person refreshed = people.findByWikidataId("Q1").orElseThrow();
        assertThat(refreshed.getId()).isEqualTo(first.getId());
        assertThat(refreshed.getNameEn()).isEqualTo("Jane Doe");
        assertThat(refreshed.getSitelinks()).isEqualTo(60);
        assertThat(refreshed.getDeathDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(refreshed.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(photos.findByPersonId(refreshed.getId()))
                .extracting(PersonPhoto::getCommonsFile)
                .containsExactlyInAnyOrder("A.jpg", "B.jpg");
    }

    @Test
    void readsTheYearAndAgeOfStoredPhotos() {
        store.save(record("Q1", "Jane", 50), BIRTH, null, List.of(photo("A.jpg", 1976), photo("B.jpg", 1990)));
        Person person = people.findByWikidataId("Q1").orElseThrow();

        assertThat(store.storedPhotos(person.getId()).kept())
                .containsExactlyInAnyOrder(new PhotoSelector.Kept(1976, 26), new PhotoSelector.Kept(1990, 40));
    }

    @Test
    void aRejectedPhotoNoLongerCountsButIsStillKnown() {
        store.save(record("Q1", "Jane", 50), BIRTH, null, List.of(photo("A.jpg", 1976), photo("B.jpg", 1990)));
        Person person = people.findByWikidataId("Q1").orElseThrow();
        PersonPhoto rejected = photos.findByPersonId(person.getId()).stream()
                .filter(p -> p.getCommonsFile().equals("A.jpg"))
                .findFirst()
                .orElseThrow();
        rejected.setReviewStatus(ReviewStatus.REJECTED);
        photos.save(rejected);

        PeopleStore.StoredPhotos stored = store.storedPhotos(person.getId());

        assertThat(stored.kept()).containsExactly(new PhotoSelector.Kept(1990, 40));
        assertThat(stored.knownFiles()).containsExactlyInAnyOrder("A.jpg", "B.jpg");
    }

    @Test
    void storesAPersonWithoutNewPhotos() {
        store.save(record("Q1", "Jane", 50), BIRTH, null, List.of());

        assertThat(people.findByWikidataId("Q1")).isPresent();
    }

    private static PersonRecord record(String qid, String name, int sitelinks) {
        return new PersonRecord(qid, sitelinks, name, null, Set.of(), List.of(), List.of());
    }

    private static NewPhoto photo(String file, int year) {
        LocalDate day = LocalDate.of(year, 7, 14);
        return new NewPhoto(
                file,
                new PhotoEvaluation.Accepted(PhotoDate.ofDay(day), PhotoDateSource.BOTH, year - 1950),
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0",
                "Someone",
                "https://commons.wikimedia.org/wiki/File:" + file,
                new ImageReencoder.Reencoded(new byte[] {1, 2, 3}, 800, 1000));
    }
}
