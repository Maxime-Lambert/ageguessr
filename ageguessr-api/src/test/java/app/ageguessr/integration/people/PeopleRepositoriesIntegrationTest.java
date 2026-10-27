package app.ageguessr.integration.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.PhotoImage;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.features.people.ReviewStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
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

    @Autowired
    private UserRepository userRepository;

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
        savePhoto(person, "Portrait.jpg", 1976);

        assertThatThrownBy(() -> savePhoto(person, "Portrait.jpg", 1980))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTwoPhotosOfOnePersonAtTheSameAge() {
        Person person = savePerson("Q1");
        // Born 1950-03-10: 26 in July 1976 and still 26 in February 1977.
        savePhoto(person, "A.jpg", 1976);

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "B.jpg", 1977)
                        .takenFrom(LocalDate.of(1977, 2, 1))
                        .takenTo(LocalDate.of(1977, 2, 28))
                        .ageAtPhoto(26)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTwoPhotosOfOnePersonInTheSameYear() {
        Person person = savePerson("Q1");
        // Born 1950-03-10: 25 in February 1976, 26 in July 1976.
        savePhoto(person, "A.jpg", 1976);

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "B.jpg", 1976)
                        .takenFrom(LocalDate.of(1976, 2, 1))
                        .takenTo(LocalDate.of(1976, 2, 29))
                        .ageAtPhoto(25)
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsTwoPeopleAtTheSameAgeInTheSameYear() {
        savePhoto(savePerson("Q1"), "A.jpg", 1976);

        PersonPhoto other = savePhoto(savePerson("Q2"), "B.jpg", 1976);

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void rejectsAnIntervalSpanningTwoYears() {
        Person person = savePerson("Q1");

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "A.jpg", 1976)
                        .takenFrom(LocalDate.of(1976, 12, 31))
                        .takenTo(LocalDate.of(1977, 1, 1))
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsTheSameCommonsFileForTwoPeople() {
        savePhoto(savePerson("Q1"), "Portrait.jpg", 1976);

        PersonPhoto other = savePhoto(savePerson("Q2"), "Portrait.jpg", 1976);

        assertThat(other.getId()).isNotNull();
    }

    @Test
    void rejectsADuplicatePublicId() {
        Person person = savePerson("Q1");
        PersonPhoto first = savePhoto(person, "A.jpg", 1976);

        assertThatThrownBy(() -> photoRepository.saveAndFlush(
                        photo(person, "B.jpg", 1980).publicId(first.getPublicId()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnAgeAboveOneHundredTwenty() {
        Person person = savePerson("Q1");

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "A.jpg", 1976).ageAtPhoto(121).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAPhotoOfAMinor() {
        Person person = savePerson("Q1");

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "A.jpg", 1967).ageAtPhoto(17).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsAPhotoAtEighteen() {
        Person person = savePerson("Q1");

        photoRepository.saveAndFlush(photo(person, "A.jpg", 1968).ageAtPhoto(18).build());

        assertThat(photoRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsAnIntervalEndingBeforeItStarts() {
        Person person = savePerson("Q1");

        assertThatThrownBy(() -> photoRepository.saveAndFlush(photo(person, "A.jpg", 1980)
                        .takenFrom(LocalDate.of(1980, 6, 2))
                        .takenTo(LocalDate.of(1980, 6, 1))
                        .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void servesTheImageOfAPhotoByItsPublicId() {
        PersonPhoto photo = savePhoto(savePerson("Q1"), "A.jpg", 1976);
        saveImage(photo);

        assertThat(imageRepository.findServableByPublicId(photo.getPublicId()))
                .get()
                .extracting(PhotoImage::getContent)
                .isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void doesNotServeAnImageByItsInternalId() {
        PersonPhoto photo = savePhoto(savePerson("Q1"), "A.jpg", 1976);
        saveImage(photo);

        assertThat(imageRepository.findServableByPublicId(photo.getId())).isEmpty();
    }

    @Test
    void doesNotServeAPhotoThatIsPendingOrRejected() {
        Person person = savePerson("Q1");
        PersonPhoto pending = photoRepository.saveAndFlush(
                photo(person, "A.jpg", 1976).reviewStatus(ReviewStatus.PENDING).build());
        PersonPhoto rejected = photoRepository.saveAndFlush(
                photo(person, "B.jpg", 1990).reviewStatus(ReviewStatus.REJECTED).build());
        saveImage(pending);
        saveImage(rejected);

        assertThat(imageRepository.findServableByPublicId(pending.getPublicId())).isEmpty();
        assertThat(imageRepository.findServableByPublicId(rejected.getPublicId())).isEmpty();
    }

    @Test
    void aRejectedPhotoFreesItsYearAndAge() {
        Person person = savePerson("Q1");
        photoRepository.saveAndFlush(photo(person, "A.jpg", 1976).reviewStatus(ReviewStatus.REJECTED).build());

        PersonPhoto sameYearAndAge = savePhoto(person, "B.jpg", 1976);

        assertThat(sameYearAndAge.getId()).isNotNull();
    }

    @Test
    void aPendingPhotoStillHoldsItsYearAndAge() {
        Person person = savePerson("Q1");
        photoRepository.saveAndFlush(photo(person, "A.jpg", 1976).reviewStatus(ReviewStatus.PENDING).build());

        assertThatThrownBy(() -> savePhoto(person, "B.jpg", 1976)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnUnknownReviewStatus() {
        Person person = savePerson("Q1");
        PersonPhoto photo = savePhoto(person, "A.jpg", 1976);

        assertThatThrownBy(() -> {
                    entityManager
                            .createNativeQuery("UPDATE person_photos SET review_status = 'MAYBE' WHERE id = :id")
                            .setParameter("id", photo.getId())
                            .executeUpdate();
                    entityManager.flush();
                })
                .hasStackTraceContaining("person_photos_review_status");
    }

    @Test
    void doesNotServeTheImageOfAnExcludedPerson() {
        Person person = personRepository.saveAndFlush(person("Q1").excluded(true).build());
        PersonPhoto photo = savePhoto(person, "A.jpg", 1976);
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
        PersonPhoto photo = savePhoto(person, "A.jpg", 1976);
        saveImage(photo);
        PersonPhoto survivor = savePhoto(savePerson("Q2"), "B.jpg", 1976);

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
    void excludingAPersonDeletesTheirPhotosAndImagesButKeepsThePerson() {
        Person person = savePerson("Q1");
        PersonPhoto first = savePhoto(person, "A.jpg", 1976);
        PersonPhoto second = savePhoto(person, "B.jpg", 1990);
        saveImage(first);
        saveImage(second);
        PersonPhoto survivor = savePhoto(savePerson("Q2"), "C.jpg", 1976);
        saveImage(survivor);

        person.setExcluded(true);
        personRepository.saveAndFlush(person);
        // The deletion happens in Postgres (trigger): bypass Hibernate's cache.
        entityManager.clear();

        assertThat(personRepository.findByWikidataId("Q1")).get().extracting(Person::isExcluded).isEqualTo(true);
        assertThat(photoRepository.findByPersonId(person.getId())).isEmpty();
        assertThat(imageRepository.findById(first.getId())).isEmpty();
        assertThat(imageRepository.findById(second.getId())).isEmpty();
        assertThat(photoRepository.findById(survivor.getId())).isPresent();
        assertThat(imageRepository.findById(survivor.getId())).isPresent();
    }

    @Test
    void updatingAnotherFieldOfAnIncludedPersonKeepsTheirPhotos() {
        Person person = savePerson("Q1");
        savePhoto(person, "A.jpg", 1976);

        person.setSitelinks(99);
        personRepository.saveAndFlush(person);
        entityManager.clear();

        assertThat(photoRepository.findByPersonId(person.getId())).hasSize(1);
    }

    @Test
    void listsPendingPhotosBestKnownPeopleFirstThenOldestShotFirst() {
        Person famous = personRepository.saveAndFlush(person("Q1").sitelinks(200).build());
        Person lessKnown = personRepository.saveAndFlush(person("Q2").sitelinks(50).build());
        PersonPhoto lessKnownPhoto = pending(lessKnown, "C.jpg", 1976);
        PersonPhoto famousLater = pending(famous, "B.jpg", 1990);
        PersonPhoto famousEarlier = pending(famous, "A.jpg", 1976);
        photoRepository.saveAndFlush(photo(famous, "Approved.jpg", 2000).reviewStatus(ReviewStatus.APPROVED).build());
        photoRepository.saveAndFlush(photo(famous, "Rejected.jpg", 2010).reviewStatus(ReviewStatus.REJECTED).build());

        assertThat(photoRepository.findPending(PageRequest.ofSize(10)))
                .extracting(p -> p.photo().getId())
                .containsExactly(famousEarlier.getId(), famousLater.getId(), lessKnownPhoto.getId());
        assertThat(photoRepository.findPending(PageRequest.ofSize(2))).hasSize(2);
        assertThat(photoRepository.countPending()).isEqualTo(3);
    }

    @Test
    void readsThePhotosOfSeveralPeopleAtOnce() {
        Person first = savePerson("Q1");
        Person second = savePerson("Q2");
        savePhoto(first, "A.jpg", 1976);
        savePhoto(second, "B.jpg", 1976);
        savePhoto(savePerson("Q3"), "C.jpg", 1976);

        assertThat(photoRepository.findByPersonIdIn(List.of(first.getId(), second.getId())))
                .extracting(PersonPhoto::getCommonsFile)
                .containsExactlyInAnyOrder("A.jpg", "B.jpg");
    }

    @Test
    void deletingTheReviewingAdminKeepsTheDecisionButForgetsWhoTookIt() {
        User admin = userRepository.saveAndFlush(User.builder()
                .email("curator@x.com")
                .username("curator")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());
        PersonPhoto photo = photoRepository.saveAndFlush(photo(savePerson("Q1"), "A.jpg", 1976)
                .reviewedBy(admin.getId())
                .reviewedAt(Instant.now())
                .build());

        userRepository.delete(admin);
        userRepository.flush();
        entityManager.clear();

        PersonPhoto reloaded = photoRepository.findById(photo.getId()).orElseThrow();
        assertThat(reloaded.getReviewedBy()).isNull();
        assertThat(reloaded.getReviewedAt()).isNotNull();
        assertThat(reloaded.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
    }

    @Test
    void searchesNamesIgnoringCaseAndAccentsInBothLanguages() {
        personRepository.saveAndFlush(person("Q1").nameEn("Pelé").nameFr(null).sitelinks(100).build());
        personRepository.saveAndFlush(person("Q2").nameEn("François Hollande").sitelinks(90).build());
        personRepository.saveAndFlush(person("Q3").nameEn(null).nameFr("Jean Dupont").sitelinks(10).build());

        assertThat(personRepository.searchByName("pele", 20)).extracting(Person::getWikidataId).containsExactly("Q1");
        assertThat(personRepository.searchByName("FRANCOIS", 20)).extracting(Person::getWikidataId).containsExactly("Q2");
        assertThat(personRepository.searchByName("dupont", 20)).extracting(Person::getWikidataId).containsExactly("Q3");
    }

    @Test
    void searchResultsComeBestKnownFirstAndAreCapped() {
        personRepository.saveAndFlush(person("Q1").nameEn("Jane A").sitelinks(10).build());
        personRepository.saveAndFlush(person("Q2").nameEn("Jane B").sitelinks(300).build());
        personRepository.saveAndFlush(person("Q3").nameEn("Jane C").sitelinks(50).build());

        assertThat(personRepository.searchByName("jane", 20)).extracting(Person::getWikidataId).containsExactly("Q2", "Q3", "Q1");
        assertThat(personRepository.searchByName("jane", 2)).hasSize(2);
    }

    @Test
    void anEscapedWildcardIsMatchedLiterally() {
        personRepository.saveAndFlush(person("Q1").nameEn("Jane Doe").build());
        personRepository.saveAndFlush(person("Q2").nameEn("Studio 100%").build());

        assertThat(personRepository.searchByName("\\%", 20)).extracting(Person::getWikidataId).containsExactly("Q2");
    }

    private PersonPhoto pending(Person person, String file, int year) {
        return photoRepository.saveAndFlush(photo(person, file, year).reviewStatus(ReviewStatus.PENDING).build());
    }

    @Test
    void listsThePhotosOfOnePerson() {
        Person person = savePerson("Q1");
        savePhoto(person, "A.jpg", 1976);
        savePhoto(person, "B.jpg", 1980);
        savePhoto(savePerson("Q2"), "C.jpg", 1976);

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

    private PersonPhoto savePhoto(Person person, String file, int year) {
        return photoRepository.saveAndFlush(photo(person, file, year).build());
    }

    /** A photo dated July of {@code year}: after the March 10 birthday, so the age is year - 1950. */
    private static PersonPhoto.PersonPhotoBuilder photo(Person person, String file, int year) {
        return PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile(file)
                .takenFrom(LocalDate.of(year, 7, 1))
                .takenTo(LocalDate.of(year, 7, 31))
                .ageAtPhoto(year - 1950)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .licenseUrl("https://creativecommons.org/licenses/by-sa/4.0")
                .authorText("Someone")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:" + file)
                .publicId(UUID.randomUUID())
                .reviewStatus(ReviewStatus.APPROVED)
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
