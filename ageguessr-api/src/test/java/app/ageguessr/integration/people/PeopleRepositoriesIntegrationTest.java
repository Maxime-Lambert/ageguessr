package app.ageguessr.integration.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.PhotoImage;
import app.ageguessr.features.people.PhotoImageRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the constraints and cascades of V4__create_people_tables.sql against a real Postgres. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PeopleRepositoriesIntegrationTest {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PersonPhotoRepository photoRepository;

    @Autowired
    private PhotoImageRepository imageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsAPersonByWikidataId() {
        Person saved = savePerson("Q1");

        assertThat(personRepository.findByWikidataId("Q1")).get().extracting(Person::getId).isEqualTo(saved.getId());
        assertThat(personRepository.findByWikidataId("Q2")).isEmpty();
    }

    @Test
    void rejectsTwoPeopleWithTheSameWikidataId() {
        savePerson("Q1");

        assertThatThrownBy(() -> savePerson("Q1")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAPersonWithoutAnyName() {
        assertThatThrownBy(() -> personRepository.saveAndFlush(person("Q1").nameEn(null).nameFr(null).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsAPersonWithOnlyAFrenchName() {
        Person saved = personRepository.saveAndFlush(person("Q1").nameEn(null).nameFr("Jean").build());

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void rejectsADeathBeforeTheBirth() {
        assertThatThrownBy(() -> personRepository.saveAndFlush(
                        person("Q1").deathDate(LocalDate.of(1949, 12, 31)).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTheSameCommonsFileTwiceForOnePerson() {
        Person person = savePerson("Q1");
        savePhoto(person, "Portrait.jpg");

        assertThatThrownBy(() -> savePhoto(person, "Portrait.jpg")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsTheSameCommonsFileForTwoPeople() {
        savePhoto(savePerson("Q1"), "Portrait.jpg");

        PersonPhoto other = savePhoto(savePerson("Q2"), "Portrait.jpg");

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void rejectsADuplicatePublicId() {
        Person person = savePerson("Q1");
        PersonPhoto first = savePhoto(person, "A.jpg");

        assertThatThrownBy(() -> photoRepository.saveAndFlush(
                        photo(person, "B.jpg").publicId(first.getPublicId()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnAgeOutsideZeroToOneHundredTwenty() {
        Person person = savePerson("Q1");

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "A.jpg").ageAtPhoto(121).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnIntervalEndingBeforeItStarts() {
        Person person = savePerson("Q1");

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "A.jpg")
                        .takenFrom(LocalDate.of(1980, 6, 2))
                        .takenTo(LocalDate.of(1980, 6, 1))
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void servesTheImageOfAPhotoByItsPublicId() {
        PersonPhoto photo = savePhoto(savePerson("Q1"), "A.jpg");
        saveImage(photo);

        assertThat(imageRepository.findServableByPublicId(photo.getPublicId()))
                .get()
                .extracting(PhotoImage::getContent)
                .isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void doesNotServeAnImageByItsInternalId() {
        PersonPhoto photo = savePhoto(savePerson("Q1"), "A.jpg");
        saveImage(photo);

        assertThat(imageRepository.findServableByPublicId(photo.getId())).isEmpty();
    }

    @Test
    void doesNotServeTheImageOfAnExcludedPerson() {
        Person person = personRepository.saveAndFlush(person("Q1").excluded(true).build());
        PersonPhoto photo = savePhoto(person, "A.jpg");
        saveImage(photo);

        assertThat(imageRepository.findServableByPublicId(photo.getPublicId())).isEmpty();
    }

    @Test
    void doesNotServeAnUnknownPublicId() {
        assertThat(imageRepository.findServableByPublicId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deletingAPersonCascadesToItsPhotosAndImages() {
        Person person = savePerson("Q1");
        PersonPhoto photo = savePhoto(person, "A.jpg");
        saveImage(photo);
        PersonPhoto survivor = savePhoto(savePerson("Q2"), "B.jpg");

        personRepository.delete(person);
        personRepository.flush();
        // Cascade happens in Postgres (ON DELETE CASCADE), not through Hibernate: clear
        // the first-level cache so the lookups below really hit the database.
        entityManager.clear();

        assertThat(photoRepository.findById(photo.getId())).isEmpty();
        assertThat(imageRepository.findById(photo.getId())).isEmpty();
        assertThat(photoRepository.findById(survivor.getId())).isPresent();
    }

    @Test
    void listsThePhotosOfOnePerson() {
        Person person = savePerson("Q1");
        savePhoto(person, "A.jpg");
        savePhoto(person, "B.jpg");
        savePhoto(savePerson("Q2"), "C.jpg");

        assertThat(photoRepository.findByPersonId(person.getId()))
                .extracting(PersonPhoto::getCommonsFile)
                .containsExactlyInAnyOrder("A.jpg", "B.jpg");
    }

    private Person savePerson(String wikidataId) {
        return personRepository.saveAndFlush(person(wikidataId).build());
    }

    private static Person.PersonBuilder person(String wikidataId) {
        return Person.builder()
                .wikidataId(wikidataId)
                .nameEn("Jane Doe")
                .birthDate(LocalDate.of(1950, 3, 10))
                .sitelinks(42)
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
    }

    private PersonPhoto savePhoto(Person person, String file) {
        return photoRepository.saveAndFlush(photo(person, file).build());
    }

    private static PersonPhoto.PersonPhotoBuilder photo(Person person, String file) {
        return PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile(file)
                .takenFrom(LocalDate.of(1976, 7, 1))
                .takenTo(LocalDate.of(1976, 7, 31))
                .ageAtPhoto(26)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .licenseUrl("https://creativecommons.org/licenses/by-sa/4.0")
                .authorText("Someone")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:" + file)
                .publicId(UUID.randomUUID())
                .createdAt(Instant.now());
    }

    private void saveImage(PersonPhoto photo) {
        imageRepository.saveAndFlush(PhotoImage.builder()
                .photoId(photo.getId())
                .content(new byte[] {1, 2, 3})
                .contentType("image/jpeg")
                .width(800)
                .height(1000)
                .build());
    }
}
