package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Person;
import java.time.Clock;
import java.time.LocalDate;
import java.util.OptionalInt;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
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
 * gender) has a quota of expected approved photos ({@link Cell#quota}): approved ones,
 * plus pending and newly added ones at the {@link ApprovalRate}, so that one wave
 * collects about what the curation will leave. The cells take turns one
 * person at a time, each walking its own ranking. A cell whose ranking runs out stays below its quota, and
 * the report says so. {@code --sample} walks the plain ranking instead, to measure the yield.
 *
 * <p>{@link IngestFocus} narrows a run: the occupation filters and the age floor go into
 * the rankings ({@link RankingFilter}), the cells limit the balanced walk, and a valid
 * photo outside the wanted age bands is left out.
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

    /**
     * What one run carries from person to person. {@code photosByAgeBand}: photos in play
     * per age band, in the database (none for a dry run) and added since, which
     * PhotoSelector leans against. {@code ranking}: who the rankings list.
     */
    private record Run(
            LocalDate today,
            boolean dryRun,
            IngestReport report,
            Map<String, Integer> photosByAgeBand,
            IngestFocus focus,
            RankingFilter ranking) {}

    IngestReport handle(IngestPeopleCommand command) {
        IngestReport report = new IngestReport();
        LocalDate today = LocalDate.now(clock);
        PoolCounts pool = command.dryRun() ? PoolCounts.EMPTY : store.poolCounts();
        Map<String, Integer> inPlayByAgeBand = new HashMap<>();
        pool.byAgeBand().forEach((band, counts) -> inPlayByAgeBand.put(band, counts.inPlay()));
        Run run = new Run(
                today,
                command.dryRun(),
                report,
                inPlayByAgeBand,
                command.focus(),
                ranking(command.focus(), today));
        if (command.wikidataId() != null) {
            wikidata.fetchPerson(command.wikidataId())
                    .ifPresentOrElse(
                            person -> process(person, run),
                            () -> log.warn("{} is not a human with a P18 image on Wikidata", command.wikidataId()));
            return report;
        }
        if (command.sample() > 0) {
            walkRanking(command, run);
        } else {
            balance(command, run, pool);
        }
        return report;
    }

    /** Where the stored pool stands against {@code targetPhotos}, without any network call. */
    PoolAnalysis analyze(int targetPhotos) {
        PoolCounts pool = store.poolCounts();
        return PoolAnalysis.of(pool, approvalRate(pool), targetPhotos);
    }

    private ApprovalRate approvalRate(PoolCounts pool) {
        return ApprovalRate.of(pool.total(), properties.expectedApprovalRate());
    }

    /**
     * Wanting only ages from A means wanting people at least A years old today: anyone
     * born after {@code today.year - A} cannot have such a photo yet, so the rankings
     * skip them.
     */
    private RankingFilter ranking(IngestFocus focus, LocalDate today) {
        OptionalInt youngest = focus.youngestAge();
        return new RankingFilter(
                properties.minBirthYear(),
                youngest.isPresent() ? OptionalInt.of(today.getYear() - youngest.getAsInt()) : OptionalInt.empty(),
                focus.excludedOccupations(),
                focus.onlyOccupations());
    }

    private void walkRanking(IngestPeopleCommand command, Run run) {
        IngestReport report = run.report();
        int offset = command.offset();
        while (!done(command, report)) {
            List<PersonRecord> page = wikidata.fetchPeople(offset, properties.pageSize(), run.ranking());
            if (page.isEmpty()) {
                log.warn("Wikidata list exhausted after {} people", report.peopleSeen());
                break;
            }
            for (PersonRecord person : page) {
                if (done(command, report)) {
                    break;
                }
                process(person, run);
            }
            offset += properties.pageSize();
        }
    }

    private void balance(IngestPeopleCommand command, Run run, PoolCounts pool) {
        IngestReport report = run.report();
        ApprovalRate rate = approvalRate(pool);
        log.info("Approval rate {}", rate);
        List<CellCursor> cursors = Cell.ALL.stream()
                .filter(command.focus()::includes)
                .map(cell -> new CellCursor(
                        cell, cell.quota(command.targetPhotos()), pool.cell(cell).expected(rate), run.ranking()))
                .toList();
        List<CellCursor> open = new ArrayList<>(cursors.stream().filter(c -> !c.full()).toList());
        while (!open.isEmpty()) {
            for (CellCursor cursor : List.copyOf(open)) {
                Optional<PersonRecord> next = cursor.next();
                if (next.isEmpty()) {
                    cursor.exhausted = true;
                    open.remove(cursor);
                    log.warn("Cell {} exhausted at {}/{} photos", cursor.cell, Math.round(cursor.expected), cursor.quota);
                    continue;
                }
                PersonRecord person = next.get();
                // Ranked here for a nationality on this continent, but counted elsewhere.
                if (!Cell.of(person).map(cursor.cell::equals).orElse(false)) {
                    continue;
                }
                // A new photo is pending: worth the approval rate of an approved one.
                cursor.expected += process(person, run) * rate.value();
                if (cursor.full()) {
                    open.remove(cursor);
                }
            }
        }
        cursors.forEach(c -> report.cellStatus(c.cell, (int) Math.round(c.expected), c.quota, c.exhausted));
    }

    /** Where one cell is in its own ranking, and how many approved photos it can expect. */
    private final class CellCursor {
        private final Cell cell;
        private final int quota;
        private final RankingFilter ranking;
        private final Deque<PersonRecord> buffer = new ArrayDeque<>();
        private double expected;
        private int offset;
        private boolean exhausted;

        private CellCursor(Cell cell, int quota, double expected, RankingFilter ranking) {
            this.cell = cell;
            this.quota = quota;
            this.expected = expected;
            this.ranking = ranking;
        }

        private boolean full() {
            return expected >= quota;
        }

        /** The next person of the cell's ranking, fetching a page when needed; empty at the end. */
        private Optional<PersonRecord> next() {
            if (buffer.isEmpty() && !exhausted) {
                buffer.addAll(wikidata.fetchCell(cell, offset, properties.pageSize(), ranking));
                offset += properties.pageSize();
            }
            return Optional.ofNullable(buffer.pollFirst());
        }
    }

    private static boolean done(IngestPeopleCommand command, IngestReport report) {
        return report.photosAdded() >= command.targetPhotos()
                || (command.sample() > 0 && report.peopleSeen() >= command.sample());
    }

    /** How many photos this person adds (0 if skipped, or if nothing new was found). */
    private int process(PersonRecord record, Run run) {
        IngestReport report = run.report();
        report.personSeen(record);
        try {
            int before = report.photosAdded();
            Optional<PersonSkip> skip = ingest(record, run);
            skip.ifPresent(reason -> {
                report.personSkipped(reason);
                log.debug("{} skipped: {}", record.wikidataId(), reason);
            });
            return report.photosAdded() - before;
        } catch (WikimediaUnavailableException e) {
            report.personSkipped(PersonSkip.FAILED);
            log.warn("{} failed, will be retried on the next run: {}", record.wikidataId(), e.getMessage());
            return 0;
        }
    }

    private Optional<PersonSkip> ingest(PersonRecord record, Run run) {
        boolean dryRun = run.dryRun();
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
                : newPhotos(record, birth.get(), death, stored, run);
        if (kept.isEmpty() && newPhotos.isEmpty()) {
            return Optional.of(PersonSkip.NO_VALID_PHOTO);
        }
        if (!dryRun) {
            store.save(record, birth.get(), death, newPhotos);
        }
        run.report().personKept(record, newPhotos);
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
            PeopleStore.StoredPhotos stored,
            Run run) {
        IngestReport report = run.report();
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
                    run.today());
            switch (evaluation) {
                case PhotoEvaluation.Rejected rejected -> report.photoRejected(rejected.reason().name());
                case PhotoEvaluation.Accepted accepted -> {
                    report.validPhoto(accepted.age());
                    if (run.focus().wantsAge(accepted.age())) {
                        candidates.add(
                                new PhotoSelector.Candidate(file.title(), mainImages.contains(file.title()), accepted));
                    } else {
                        report.photoRejected(PhotoRejection.AGE_BAND_NOT_WANTED.name());
                    }
                }
            }
        }

        List<PhotoSelector.Candidate> selected = PhotoSelector.select(
                stored.kept(),
                candidates,
                properties.maxPhotosPerPerson(),
                properties.minYearsBetweenPhotos(),
                run.photosByAgeBand());
        // Valid, but too close in age to a kept photo, or over the per-person limit.
        report.photosNotSelected(candidates.size() - selected.size());

        List<NewPhoto> newPhotos = new ArrayList<>();
        for (PhotoSelector.Candidate candidate : selected) {
            CommonsFile file = files.get(candidate.fileTitle());
            try {
                ImageReencoder.Reencoded image = reencoder.reencode(downloader.download(file.thumbnailUrl()));
                report.photoKept(candidate.age());
                run.photosByAgeBand().merge(AgeBand.of(candidate.age()), 1, Integer::sum);
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
