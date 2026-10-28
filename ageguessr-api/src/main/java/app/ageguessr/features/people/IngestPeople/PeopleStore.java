package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.ExclusionReason;
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
     * The stored photos per cell and per age band, by review status. A rerun, after a
     * curation pass for instance, tops each cell back up to its quota of expected
     * approved photos, and keeps leaning toward the rare ages.
     */
    @Transactional(readOnly = true)
    PoolCounts poolCounts() {
        Map<Cell, ReviewCounts> byCell = people.countPhotosByCell().stream()
                .collect(Collectors.toMap(
                        c -> new Cell(c.continent(), c.gender()),
                        c -> ReviewCounts.of(c.status(), Math.toIntExact(c.photos())),
                        ReviewCounts::plus));
        Map<String, ReviewCounts> byAgeBand = photos.countByAge().stream()
                .collect(Collectors.toMap(
                        c -> AgeBand.of(c.age()),
                        c -> ReviewCounts.of(c.status(), Math.toIntExact(c.photos())),
                        ReviewCounts::plus));
        return new PoolCounts(byCell, byAgeBand);
    }

    /** QIDs of every person still in the pool. */
    @Transactional(readOnly = true)
    List<String> storedWikidataIds() {
        return people.findWikidataIdsNotExcluded();
    }

    /** A person's photos per review status. */
    @Transactional(readOnly = true)
    ReviewCounts photosOf(String wikidataId) {
        return people.findByWikidataId(wikidataId)
                .map(person -> photos.findByPersonId(person.getId()).stream()
                        .map(photo -> ReviewCounts.of(photo.getReviewStatus(), 1))
                        .reduce(ReviewCounts.NONE, ReviewCounts::plus))
                .orElse(ReviewCounts.NONE);
    }

    /** Excludes a person under the editorial line: the V4 trigger deletes their photos, approved ones too. */
    @Transactional
    void excludeForEditorialLine(String wikidataId) {
        Person person = people.findByWikidataId(wikidataId).orElseThrow();
        person.exclude(ExclusionReason.EDITORIAL_LINE);
        people.saveAndFlush(person);
    }

    /** Photos still counting whose image is shorter than {@code minHeight}, by file name. */
    @Transactional(readOnly = true)
    List<PersonPhotoRepository.SmallImage> smallImages(int minHeight) {
        return photos.findSmallImages(minHeight);
    }

    /** Swaps a photo's image for a larger copy of the same file; its review status stays. */
    @Transactional
    void replaceImage(UUID photoId, ImageReencoder.Reencoded image) {
        PhotoImage stored = images.findById(photoId).orElseThrow();
        stored.setContent(image.content());
        stored.setWidth(image.width());
        stored.setHeight(image.height());
    }

    /**
     * Rejects a photo without a curator ({@code reviewed_by} stays empty): its file has no
     * copy tall enough. An approved photo is rejected too, it cannot be shown.
     */
    @Transactional
    void rejectTooSmall(UUID photoId) {
        PersonPhoto photo = photos.findById(photoId).orElseThrow();
        photo.setReviewStatus(ReviewStatus.REJECTED);
        photo.setReviewedAt(Instant.now(clock));
        photo.setReviewedBy(null);
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
