package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Person;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Builds the pool of people: Wikidata gives the people, Commons their photos, the date
 * rules decide which photos give an exact age, and the kept ones are re-encoded and
 * stored. Safe to run again: existing photos are kept and count toward the per-person
 * limit and minimum gap, so a rerun only adds what is missing. A dry run never touches
 * the store: every person is processed as if new, and nothing is saved.
 *
 * <p>By default the pool is balanced: each {@link Cell} (continent of nationality ×
 * gender) gets the same quota, the cells taking turns one person at a time, each walking
 * its own ranking. A cell whose ranking runs out stays below its quota, and the report
 * says so. {@code --sample} walks the plain ranking instead, to measure the yield.
 */
@Slf4j
@Service
class IngestPeopleHandler {

    private static final String FILE_PREFIX = "File:";

    private final WikidataClient wikidata;
    private final CommonsClient commons;
    private final ImageDownloader downloader;
    private final ImageReencoder reencoder;
    private final PeopleStore store;
    private final IngestProperties properties;
    private final Clock clock;

    IngestPeopleHandler(
            WikidataClient wikidata,
            CommonsClient commons,
            ImageDownloader downloader,
            ImageReencoder reencoder,
            PeopleStore store,
            IngestProperties properties,
            Clock clock) {
        this.wikidata = wikidata;
        this.commons = commons;
        this.downloader = downloader;
        this.reencoder = reencoder;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
    }

    IngestReport handle(IngestPeopleCommand command) {
        IngestReport report = new IngestReport();
        LocalDate today = LocalDate.now(clock);
        if (command.wikidataId() != null) {
            wikidata.fetchPerson(command.wikidataId())
                    .ifPresentOrElse(
                            person -> process(person, today, command.dryRun(), report),
                            () -> log.warn("{} is not a human with a P18 image on Wikidata", command.wikidataId()));
            return report;
        }
        if (command.sample() > 0) {
            walkRanking(command, today, report);
        } else {
            balance(command, today, report);
        }
        return report;
    }

    private void walkRanking(IngestPeopleCommand command, LocalDate today, IngestReport report) {
        int offset = command.offset();
        while (!done(command, report)) {
            List<PersonRecord> page = wikidata.fetchPeople(offset, properties.pageSize(), properties.minBirthYear());
            if (page.isEmpty()) {
                log.warn("Wikidata list exhausted before reaching {} people", command.targetPeople());
                break;
            }
            for (PersonRecord person : page) {
                if (done(command, report)) {
                    break;
                }
                process(person, today, command.dryRun(), report);
            }
            offset += properties.pageSize();
        }
    }

    private void balance(IngestPeopleCommand command, LocalDate today, IngestReport report) {
        int quota = Math.ceilDiv(command.targetPeople(), Cell.SHARING_THE_TARGET);
        Map<Cell, Set<String>> counted = command.dryRun() ? Map.of() : store.balancedPeople();
        List<CellCursor> cursors = Cell.ALL.stream()
                .map(cell -> new CellCursor(cell, counted.getOrDefault(cell, Set.of())))
                .toList();
        List<CellCursor> open = new ArrayList<>(cursors.stream().filter(c -> !c.full(quota)).toList());
        while (!open.isEmpty()) {
            for (CellCursor cursor : List.copyOf(open)) {
                Optional<PersonRecord> next = cursor.next();
                if (next.isEmpty()) {
                    cursor.exhausted = true;
                    open.remove(cursor);
                    log.warn("Cell {} exhausted at {}/{} people", cursor.cell, cursor.people.size(), quota);
                    continue;
                }
                PersonRecord person = next.get();
                // Ranked here for a nationality on this continent, but counted elsewhere.
                if (!Cell.of(person).map(cursor.cell::equals).orElse(false)) {
                    continue;
                }
                if (process(person, today, command.dryRun(), report)) {
                    cursor.people.add(person.wikidataId());
                    if (cursor.full(quota)) {
                        open.remove(cursor);
                    }
                }
            }
        }
        cursors.forEach(c -> report.cellStatus(c.cell, c.people.size(), quota, c.exhausted));
    }

    /** Where one cell is in its own ranking, and who already counts in it. */
    private final class CellCursor {
        private final Cell cell;
        private final Set<String> people;
        private final Deque<PersonRecord> buffer = new ArrayDeque<>();
        private int offset;
        private boolean exhausted;

        private CellCursor(Cell cell, Set<String> alreadyCounted) {
            this.cell = cell;
            this.people = new HashSet<>(alreadyCounted);
        }

        private boolean full(int quota) {
            return people.size() >= quota;
        }

        /** The next person of the cell's ranking, fetching a page when needed; empty at the end. */
        private Optional<PersonRecord> next() {
            if (buffer.isEmpty() && !exhausted) {
                buffer.addAll(wikidata.fetchCell(cell, offset, properties.pageSize(), properties.minBirthYear()));
                offset += properties.pageSize();
            }
            return Optional.ofNullable(buffer.pollFirst());
        }
    }

    private static boolean done(IngestPeopleCommand command, IngestReport report) {
        return report.peopleKept() >= command.targetPeople()
                || (command.sample() > 0 && report.peopleSeen() >= command.sample());
    }

    /** True if the person is kept, with at least one photo. */
    private boolean process(PersonRecord record, LocalDate today, boolean dryRun, IngestReport report) {
        report.personSeen(record);
        try {
            Optional<PersonSkip> skip = ingest(record, today, dryRun, report);
            skip.ifPresent(reason -> {
                report.personSkipped(reason);
                log.debug("{} skipped: {}", record.wikidataId(), reason);
            });
            return skip.isEmpty();
        } catch (WikimediaUnavailableException e) {
            report.personSkipped(PersonSkip.FAILED);
            log.warn("{} failed, will be retried on the next run: {}", record.wikidataId(), e.getMessage());
            return false;
        }
    }

    private Optional<PersonSkip> ingest(PersonRecord record, LocalDate today, boolean dryRun, IngestReport report) {
        Optional<Person> existing = dryRun ? Optional.empty() : store.find(record.wikidataId());
        if (existing.map(Person::isExcluded).orElse(false)) {
            return Optional.of(PersonSkip.EXCLUDED);
        }
        Optional<LocalDate> birth = BirthDateSelector.select(record.births());
        if (birth.isEmpty()) {
            return Optional.of(PersonSkip.BIRTH_DATE_UNUSABLE);
        }
        DeathDateSelector.Outcome deathOutcome = DeathDateSelector.select(record.deaths());
        if (deathOutcome instanceof DeathDateSelector.Unusable) {
            return Optional.of(PersonSkip.DEATH_DATE_UNUSABLE);
        }
        LocalDate death = deathOutcome instanceof DeathDateSelector.Died died ? died.earliestPossibleDay() : null;
        if (record.nameEn() == null && record.nameFr() == null) {
            return Optional.of(PersonSkip.NO_NAME);
        }
        if (existing.isPresent() && !existing.get().getBirthDate().equals(birth.get())) {
            log.warn(
                    "{}: birth date changed on Wikidata ({} stored, {} now), left untouched for a manual check",
                    record.wikidataId(), existing.get().getBirthDate(), birth.get());
            return Optional.of(PersonSkip.BIRTH_DATE_CHANGED);
        }

        PeopleStore.StoredPhotos stored =
                existing.map(p -> store.storedPhotos(p.getId())).orElse(PeopleStore.StoredPhotos.NONE);
        List<PhotoSelector.Kept> kept = stored.kept();
        List<NewPhoto> newPhotos = kept.size() >= properties.maxPhotosPerPerson()
                ? List.of()
                : newPhotos(record, birth.get(), death, today, stored, report);
        if (kept.isEmpty() && newPhotos.isEmpty()) {
            return Optional.of(PersonSkip.NO_VALID_PHOTO);
        }
        if (!dryRun) {
            store.save(record, birth.get(), death, newPhotos);
        }
        report.personKept(record, newPhotos);
        // QIDs only in logs, never names or file titles (which usually contain the name):
        // a person removed on request must not linger in log files.
        log.info(
                "{}: {} photo(s) {}, {} already stored",
                record.wikidataId(), newPhotos.size(), dryRun ? "found (dry run)" : "added", kept.size());
        return Optional.empty();
    }

    private List<NewPhoto> newPhotos(
            PersonRecord record,
            LocalDate birth,
            LocalDate death,
            LocalDate today,
            PeopleStore.StoredPhotos stored,
            IngestReport report) {
        Set<String> mainImages = new LinkedHashSet<>();
        record.mainImageFiles().forEach(file -> mainImages.add(FILE_PREFIX + file));
        Set<String> titles = new LinkedHashSet<>(commons.searchDepicting(record.wikidataId()));
        titles.addAll(mainImages);

        Map<String, CommonsFile> files = commons.fetchFiles(titles);
        Map<Long, MediaInfo> mediaInfo = commons.fetchMediaInfo(
                files.values().stream().map(CommonsFile::pageId).toList());

        List<PhotoSelector.Candidate> candidates = new ArrayList<>();
        for (CommonsFile file : files.values()) {
            // Already stored, whatever its review status: a rejected photo stays rejected.
            if (stored.knownFiles().contains(file.title().substring(FILE_PREFIX.length()))) {
                continue;
            }
            MediaInfo info = mediaInfo.getOrDefault(file.pageId(), MediaInfo.NONE);
            Optional<PhotoRejection> refused = PhotoCandidateFilter.check(file, info);
            if (refused.isPresent()) {
                report.photoRejected(refused.get().name());
                continue;
            }
            PhotoEvaluation evaluation = PhotoEvaluator.evaluate(
                    new PhotoEvaluator.Evidence(
                            file.title(), info.inceptions(), file.informationDate(), file.exifDateTimeOriginal()),
                    birth,
                    death,
                    today);
            switch (evaluation) {
                case PhotoEvaluation.Rejected rejected -> report.photoRejected(rejected.reason().name());
                case PhotoEvaluation.Accepted accepted -> {
                    report.validPhoto(accepted.age());
                    candidates.add(new PhotoSelector.Candidate(file.title(), mainImages.contains(file.title()), accepted));
                }
            }
        }

        List<PhotoSelector.Candidate> selected = PhotoSelector.select(
                stored.kept(), candidates, properties.maxPhotosPerPerson(), properties.minYearsBetweenPhotos());
        // Valid, but too close in age to a kept photo, or over the per-person limit.
        report.photosNotSelected(candidates.size() - selected.size());

        List<NewPhoto> newPhotos = new ArrayList<>();
        for (PhotoSelector.Candidate candidate : selected) {
            CommonsFile file = files.get(candidate.fileTitle());
            try {
                ImageReencoder.Reencoded image = reencoder.reencode(downloader.download(file.thumbnailUrl()));
                report.photoKept(candidate.age());
                newPhotos.add(new NewPhoto(
                        file.title().substring(FILE_PREFIX.length()),
                        candidate.evaluation(),
                        file.licenseShortName(),
                        file.licenseUrl(),
                        AttributionSanitizer.toPlainText(file.artistHtml()),
                        file.descriptionUrl(),
                        image));
            } catch (ImageRejectedException e) {
                report.photoRejected("IMAGE_" + e.reason().name());
                log.warn("{}: an image was rejected ({})", record.wikidataId(), e.reason());
            }
        }
        return newPhotos;
    }
}
