package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.features.people.ReviewStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

        store.save(
                new PersonRecord("Q1", 60, "Jane Doe", "Jeanne Doe", Set.of(), List.of(), List.of(), null, null, Set.of()),
                BIRTH,
                LocalDate.of(2020, 1, 1),
                List.of(photo("B.jpg", 1990)));

        assertThat(people.findAll()).hasSize(1);
        Person refreshed = people.findByWikidataId("Q1").orElseThrow();
        assertThat(refreshed.getId()).isEqualTo(first.getId());
        assertThat(refreshed.getNameEn()).isEqualTo("Jane Doe");
        assertThat(refreshed.getNameFr()).isEqualTo("Jeanne Doe");
        assertThat(refreshed.getSitelinks()).isEqualTo(60);
        assertThat(refreshed.getDeathDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(refreshed.getCreatedAt()).isEqualTo(first.getCreatedAt());
        assertThat(photos.findByPersonId(refreshed.getId()))
                .extracting(PersonPhoto::getCommonsFile)
                .containsExactlyInAnyOrder("A.jpg", "B.jpg");
    }

    @Test
    void storesTheGenderAndContinentAndRefreshesThemOnTheNextRun() {
        store.save(record("Q1", "Jane", 50), BIRTH, null, List.of());
        assertThat(people.findByWikidataId("Q1").orElseThrow())
                .extracting(Person::getGender, Person::getContinent)
                .containsExactly(null, null);

        store.save(
                new PersonRecord(
                        "Q1", 50, "Jane", null, Set.of(), List.of(), List.of(), Gender.FEMALE, Continent.AFRICA, Set.of()),
                BIRTH,
                null,
                List.of());

        assertThat(people.findByWikidataId("Q1").orElseThrow())
                .extracting(Person::getGender, Person::getContinent)
                .containsExactly(Gender.FEMALE, Continent.AFRICA);
    }

    @Test
    void countsPerCellAndStatusThePhotosOfPeopleNotExcluded() {
        store.save(person("Q1", Gender.FEMALE, Continent.AFRICA), BIRTH, null, List.of(photo("A.jpg", 1976)));
        store.save(person("Q2", Gender.FEMALE, Continent.AFRICA), BIRTH, null, List.of(photo("B.jpg", 1976)));
        store.save(person("Q3", Gender.MALE, Continent.ASIA), BIRTH, null, List.of(photo("C.jpg", 1976)));
        // Q4 has no photo, Q5 only a rejected one. Not counted: excluded, cell unknown.
        store.save(person("Q4", Gender.FEMALE, Continent.AFRICA), BIRTH, null, List.of());
        store.save(person("Q5", Gender.FEMALE, Continent.AFRICA), BIRTH, null, List.of(photo("E.jpg", 1976)));
        photos.findAll().stream()
                .filter(p -> p.getCommonsFile().equals("E.jpg"))
                .forEach(p -> {
                    p.setReviewStatus(ReviewStatus.REJECTED);
                    photos.save(p);
                });
        store.save(person("Q6", Gender.FEMALE, Continent.AFRICA), BIRTH, null, List.of(photo("F.jpg", 1976)));
        Person excluded = people.findByWikidataId("Q6").orElseThrow();
        excluded.setExcluded(true);
        people.save(excluded);
        store.save(record("Q7", "Unknown", 50), BIRTH, null, List.of(photo("G.jpg", 1976)));

        assertThat(store.poolCounts().byCell())
                .isEqualTo(Map.of(
                        new Cell(Continent.AFRICA, Gender.FEMALE), new ReviewCounts(0, 2, 1),
                        new Cell(Continent.ASIA, Gender.MALE), new ReviewCounts(0, 1, 0)));
    }

    @Test
    void countsThePhotosPerAgeBandAndStatus() {
        // Born 1950: 26 in 1976, 30 in 1980, 40 in 1990.
        store.save(record("Q1", "Jane", 50), BIRTH, null, List.of(photo("A.jpg", 1976), photo("B.jpg", 1990)));
        store.save(record("Q2", "Sam", 50), BIRTH, null, List.of(photo("C.jpg", 1980), photo("D.jpg", 1990)));
        photos.findAll().stream()
                .filter(p -> p.getCommonsFile().equals("D.jpg"))
                .forEach(p -> {
                    p.setReviewStatus(ReviewStatus.REJECTED);
                    photos.save(p);
                });
        store.save(record("Q3", "Excluded", 50), BIRTH, null, List.of(photo("E.jpg", 1980)));
        Person excluded = people.findByWikidataId("Q3").orElseThrow();
        excluded.setExcluded(true);
        people.save(excluded);

        Person approvedOne = people.findByWikidataId("Q1").orElseThrow();
        photos.findByPersonId(approvedOne.getId()).stream()
                .filter(p -> p.getCommonsFile().equals("A.jpg"))
                .forEach(p -> {
                    p.setReviewStatus(ReviewStatus.APPROVED);
                    photos.save(p);
                });

        assertThat(store.poolCounts().byAgeBand())
                .isEqualTo(Map.of("26-33", new ReviewCounts(1, 1, 0), "34-41", new ReviewCounts(0, 1, 1)));
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

    private static PersonRecord person(String qid, Gender gender, Continent continent) {
        return new PersonRecord(qid, 50, qid, null, Set.of(), List.of(), List.of(), gender, continent, Set.of());
    }

    private static PersonRecord record(String qid, String name, int sitelinks) {
        return new PersonRecord(qid, sitelinks, name, null, Set.of(), List.of(), List.of(), null, null, Set.of());
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
