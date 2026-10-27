package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoImage;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.features.people.ReviewStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database side of the ingestion, one transaction per person: a person is stored with
 * all their new photos or not at all. Network calls happen before, outside any
 * transaction, so a slow Wikimedia never holds a database connection.
 */
@Component
class PeopleStore {

    private final PersonRepository people;
    private final PersonPhotoRepository photos;
    private final PhotoImageRepository images;
    private final Clock clock;

    PeopleStore(PersonRepository people, PersonPhotoRepository photos, PhotoImageRepository images, Clock clock) {
        this.people = people;
        this.photos = photos;
        this.images = images;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Optional<Person> find(String wikidataId) {
        return people.findByWikidataId(wikidataId);
    }

    /**
     * QIDs of the people already counting toward each cell (see
     * PersonRepository.findBalanced): a rerun goes on from there instead of starting over.
     */
    @Transactional(readOnly = true)
    Map<Cell, Set<String>> balancedPeople() {
        return people.findBalanced().stream()
                .collect(Collectors.groupingBy(
                        p -> new Cell(p.continent(), p.gender()),
                        Collectors.mapping(PersonRepository.BalancedPerson::wikidataId, Collectors.toSet())));
    }

    /** Photos still in play per age band, so that a rerun keeps leaning toward the rare ages. */
    @Transactional(readOnly = true)
    Map<String, Integer> photosByAgeBand() {
        return photos.countByAge().stream()
                .collect(Collectors.toMap(
                        c -> AgeBand.of(c.age()), c -> Math.toIntExact(c.photos()), Integer::sum));
    }

    /** What a person already has, as far as the next ingestion is concerned. */
    record StoredPhotos(List<PhotoSelector.Kept> kept, Set<String> knownFiles) {

        static final StoredPhotos NONE = new StoredPhotos(List.of(), Set.of());
    }

    /**
     * {@code kept}: photos that still count, pending or approved. A rejected photo does
     * not, so it frees its year and age. {@code knownFiles}: every file already stored,
     * rejected ones included, so a rejected photo is never proposed again.
     */
    @Transactional(readOnly = true)
    StoredPhotos storedPhotos(UUID personId) {
        List<PersonPhoto> all = photos.findByPersonId(personId);
        return new StoredPhotos(
                all.stream()
                        .filter(p -> p.getReviewStatus() != ReviewStatus.REJECTED)
                        .map(p -> new PhotoSelector.Kept(p.getTakenFrom().getYear(), p.getAgeAtPhoto()))
                        .toList(),
                all.stream().map(PersonPhoto::getCommonsFile).collect(Collectors.toUnmodifiableSet()));
    }

    /** Creates or refreshes the person, then adds the new photos. Existing photos are never touched. */
    @Transactional
    void save(PersonRecord record, LocalDate birth, LocalDate death, List<NewPhoto> newPhotos) {
        Instant now = clock.instant();
        Person person = people.findByWikidataId(record.wikidataId())
                .orElseGet(() -> Person.builder()
                        .wikidataId(record.wikidataId())
                        .createdAt(now)
                        .build());
        person.setNameEn(record.nameEn());
        person.setNameFr(record.nameFr());
        person.setBirthDate(birth);
        person.setDeathDate(death);
        person.setSitelinks(record.sitelinks());
        person.setGender(record.gender());
        person.setContinent(record.continent());
        person.setUpdatedAt(now);
        Person saved = people.save(person);

        for (NewPhoto photo : newPhotos) {
            PersonPhoto stored = photos.save(PersonPhoto.builder()
                    .personId(saved.getId())
                    .commonsFile(photo.commonsFile())
                    .takenFrom(photo.evaluation().date().from())
                    .takenTo(photo.evaluation().date().to())
                    .ageAtPhoto(photo.evaluation().age())
                    .dateSource(photo.evaluation().source())
                    .licenseShortName(photo.licenseShortName())
                    .licenseUrl(photo.licenseUrl())
                    .authorText(photo.authorText())
                    .sourceUrl(photo.sourceUrl())
                    .publicId(UUID.randomUUID())
                    .reviewStatus(ReviewStatus.PENDING)
                    .createdAt(now)
                    .build());
            images.save(PhotoImage.builder()
                    .photoId(stored.getId())
                    .content(photo.image().content())
                    .contentType(ImageReencoder.CONTENT_TYPE)
                    .width(photo.image().width())
                    .height(photo.image().height())
                    .build());
        }
    }
}
