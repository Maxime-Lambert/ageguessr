package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.people.Person;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Everyone here is born on 1950-03-10. A photo dated July of year Y is therefore Y - 1950 years old. */
class IngestPeopleHandlerTest {

    private static final LocalDate BIRTH = LocalDate.of(1950, 3, 10);
    private static final ImageReencoder.Reencoded IMAGE = new ImageReencoder.Reencoded(new byte[] {1}, 800, 1000);

    private final WikidataClient wikidata = mock(WikidataClient.class);
    private final CommonsClient commons = mock(CommonsClient.class);
    private final ImageDownloader downloader = mock(ImageDownloader.class);
    private final ImageReencoder reencoder = mock(ImageReencoder.class);
    private final PeopleStore store = mock(PeopleStore.class);
    private final IngestProperties properties = new IngestProperties(3000, 3, 5, 10, 50, 800, 5_000_000);
    private final Clock clock = Clock.fixed(java.time.Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC);

    private IngestPeopleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IngestPeopleHandler(wikidata, commons, downloader, reencoder, store, properties, clock);
        when(store.find(anyString())).thenReturn(Optional.empty());
        when(downloader.download(anyString())).thenReturn(new byte[] {9});
        when(reencoder.reencode(any())).thenReturn(IMAGE);
    }

    @Test
    void storesAPersonWithTheirDatedPhotos() {
        PersonRecord jane = person("Q1");
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(jane));
        photos("Q1", photo("File:Jane 1976.jpg", 1976), photo("File:Jane 1990.jpg", 1990));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(eq(jane), eq(BIRTH), eq(null), saved.capture());
        assertThat(saved.getValue())
                .extracting(NewPhoto::commonsFile, p -> p.evaluation().age())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Jane 1976.jpg", 26),
                        org.assertj.core.groups.Tuple.tuple("Jane 1990.jpg", 40));
        assertThat(saved.getValue().getFirst().image()).isEqualTo(IMAGE);
        assertThat(report.peopleKept()).isEqualTo(1);
        assertThat(report.photosAdded()).isEqualTo(2);
    }

    @Test
    void searchesBothTheDepictingFilesAndTheMainWikidataImage() {
        PersonRecord jane = person("Q1");
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(jane));
        when(commons.searchDepicting("Q1")).thenReturn(List.of("File:Other.jpg"));
        when(commons.fetchFiles(anyCollection())).thenReturn(Map.of());

        handler.handle(new IngestPeopleCommand(1, null));

        verify(commons).fetchFiles(Set.of("File:Other.jpg", "File:Jane main.jpg"));
    }

    @Test
    void stopsOnceTheTargetIsReached() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1"), person("Q2"), person("Q3")));
        photos("Q1", photo("File:A 1976.jpg", 1976));
        photos("Q2", photo("File:B 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(2, null));

        assertThat(report.peopleKept()).isEqualTo(2);
        assertThat(report.peopleSeen()).isEqualTo(2);
        verify(commons, never()).searchDepicting("Q3");
        verify(wikidata, times(1)).fetchPeople(anyInt(), anyInt());
    }

    @Test
    void goesToTheNextPageUntilTheTargetIsReached() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1"), person("Q2"), person("Q3")));
        when(wikidata.fetchPeople(3, 3)).thenReturn(List.of(person("Q4")));
        photos("Q1", photo("File:A 1976.jpg", 1976));
        noPhotos("Q2");
        noPhotos("Q3");
        photos("Q4", photo("File:D 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(2, null));

        assertThat(report.peopleKept()).isEqualTo(2);
        assertThat(report.peopleSkipped()).containsEntry("NO_VALID_PHOTO", 2);
    }

    @Test
    void stopsWhenWikidataHasNoMorePeople() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")));
        when(wikidata.fetchPeople(3, 3)).thenReturn(List.of());
        photos("Q1", photo("File:A 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(10, null));

        assertThat(report.peopleKept()).isEqualTo(1);
    }

    @Test
    void neverReingestsAnExcludedPerson() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")), List.of());
        when(store.find("Q1")).thenReturn(Optional.of(stored("Q1", BIRTH, true)));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.peopleSkipped()).containsEntry("EXCLUDED", 1);
        verifyNoInteractions(commons);
        verify(store, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void skipsAPersonWithoutATrustworthyBirthDate() {
        PersonRecord yearOnly = new PersonRecord(
                "Q1", 50, "Jane", null, Set.of("Jane main.jpg"),
                List.of(new BirthStatement("1950-00-00T00:00:00Z", 9, BirthDateSelector.GREGORIAN, BirthStatement.Rank.NORMAL)),
                List.of());
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(yearOnly), List.of());

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.peopleSkipped()).containsEntry("BIRTH_DATE_UNUSABLE", 1);
        verifyNoInteractions(commons);
    }

    @Test
    void skipsAPersonWhoseDeathDateIsUnknown() {
        PersonRecord unknownDeath = new PersonRecord(
                "Q1", 50, "Jane", null, Set.of("Jane main.jpg"), List.of(birth()),
                List.of(new DeathStatement(null, null, BirthStatement.Rank.NORMAL)));
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(unknownDeath), List.of());

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.peopleSkipped()).containsEntry("DEATH_DATE_UNUSABLE", 1);
    }

    @Test
    void passesTheDeathDateToTheRulesAndTheStore() {
        PersonRecord died = new PersonRecord(
                "Q1", 50, "Jane", null, Set.of("Jane main.jpg"), List.of(birth()),
                List.of(new DeathStatement("1985-01-01T00:00:00Z", 11, BirthStatement.Rank.NORMAL)));
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(died));
        photos("Q1", photo("File:Alive 1976.jpg", 1976), photo("File:Grave 1990.jpg", 1990));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(eq(died), eq(BIRTH), eq(LocalDate.of(1985, 1, 1)), saved.capture());
        assertThat(saved.getValue()).extracting(NewPhoto::commonsFile).containsExactly("Alive 1976.jpg");
        assertThat(report.photosRejected()).containsEntry("OUTSIDE_LIFETIME", 1);
    }

    @Test
    void skipsAPersonWithoutAnyName() {
        PersonRecord nameless = new PersonRecord("Q1", 50, null, null, Set.of("X.jpg"), List.of(birth()), List.of());
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(nameless), List.of());

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.peopleSkipped()).containsEntry("NO_NAME", 1);
    }

    @Test
    void leavesAPersonAloneWhenWikidataChangedTheirBirthDate() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")), List.of());
        when(store.find("Q1")).thenReturn(Optional.of(stored("Q1", LocalDate.of(1951, 1, 1), false)));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.peopleSkipped()).containsEntry("BIRTH_DATE_CHANGED", 1);
        verifyNoInteractions(commons);
        verify(store, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void doesNotStoreAPersonWithoutAnyValidPhoto() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")), List.of());
        photos("Q1", new CommonsFileAndInfo(file("File:Undated.jpg", null, null), MediaInfo.NONE));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.peopleSkipped()).containsEntry("NO_VALID_PHOTO", 1);
        assertThat(report.photosRejected()).containsEntry("NOT_DATED", 1);
        verify(store, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void countsPhotosDroppedByTheMinimumGap() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1980.jpg", 1980));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.photosAdded()).isEqualTo(1);
        assertThat(report.photosRejected()).containsEntry("NOT_SELECTED", 1);
    }

    @Test
    void aRerunKeepsStoredPhotosAndOnlyAddsWhatIsFarEnoughFromThem() {
        Person existing = stored("Q1", BIRTH, false);
        when(store.find("Q1")).thenReturn(Optional.of(existing));
        when(store.storedPhotos(existing.getId()))
                .thenReturn(new PeopleStore.StoredPhotos(List.of(new PhotoSelector.Kept(1976, 26)), Set.of("Old.jpg")));
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1990.jpg", 1990));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(any(), any(), any(), saved.capture());
        assertThat(saved.getValue()).extracting(NewPhoto::commonsFile).containsExactly("B 1990.jpg");
        assertThat(report.peopleKept()).isEqualTo(1);
    }

    @Test
    void aPersonAlreadyAtTheLimitIsRefreshedWithoutAnyCommonsCall() {
        Person existing = stored("Q1", BIRTH, false);
        when(store.find("Q1")).thenReturn(Optional.of(existing));
        when(store.storedPhotos(existing.getId()))
                .thenReturn(new PeopleStore.StoredPhotos(
                        List.of(
                                new PhotoSelector.Kept(1960, 10), new PhotoSelector.Kept(1970, 20),
                                new PhotoSelector.Kept(1980, 30), new PhotoSelector.Kept(1990, 40),
                                new PhotoSelector.Kept(2000, 50)),
                        Set.of()));
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        verifyNoInteractions(commons);
        verify(store).save(any(), eq(BIRTH), any(), eq(List.of()));
        assertThat(report.peopleKept()).isEqualTo(1);
        assertThat(report.photosAdded()).isZero();
    }

    @Test
    void neverProposesAgainAFileAlreadyStoredEvenIfItWasRejected() {
        Person existing = stored("Q1", BIRTH, false);
        when(store.find("Q1")).thenReturn(Optional.of(existing));
        // A.jpg was rejected at curation: it no longer counts, but it is still known.
        when(store.storedPhotos(existing.getId()))
                .thenReturn(new PeopleStore.StoredPhotos(List.of(), Set.of("A 1976.jpg")));
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1977.jpg", 1977));

        handler.handle(new IngestPeopleCommand(1, null));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(any(), any(), any(), saved.capture());
        // B is only one year away from the rejected A, which no longer blocks anything.
        assertThat(saved.getValue()).extracting(NewPhoto::commonsFile).containsExactly("B 1977.jpg");
    }

    @Test
    void oneFailingPersonDoesNotStopTheRun() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1"), person("Q2")));
        when(commons.searchDepicting("Q1")).thenThrow(new WikimediaUnavailableException("commons down"));
        photos("Q2", photo("File:B 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        assertThat(report.peopleSkipped()).containsEntry("FAILED", 1);
        assertThat(report.peopleKept()).isEqualTo(1);
    }

    @Test
    void aRejectedImageSkipsOnlyThatPhoto() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1990.jpg", 1990));
        when(downloader.download("https://upload.wikimedia.org/File:A 1976.jpg"))
                .thenThrow(new ImageRejectedException(ImageRejectedException.Reason.TOO_LARGE, "too big"));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(any(), any(), any(), saved.capture());
        assertThat(saved.getValue()).extracting(NewPhoto::commonsFile).containsExactly("B 1990.jpg");
        assertThat(report.photosRejected()).containsEntry("IMAGE_TOO_LARGE", 1);
    }

    @Test
    void storesTheAuthorAsPlainText() {
        when(wikidata.fetchPeople(0, 3)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976));

        handler.handle(new IngestPeopleCommand(1, null));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(any(), any(), any(), saved.capture());
        assertThat(saved.getValue().getFirst().authorText()).isEqualTo("Some Photographer");
        assertThat(saved.getValue().getFirst().sourceUrl())
                .isEqualTo("https://commons.wikimedia.org/wiki/File:A 1976.jpg");
    }

    @Test
    void ingestsASinglePersonByQid() {
        when(wikidata.fetchPerson("Q42")).thenReturn(Optional.of(person("Q42")));
        photos("Q42", photo("File:A 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(3000, "Q42"));

        assertThat(report.peopleKept()).isEqualTo(1);
        verify(wikidata, never()).fetchPeople(anyInt(), anyInt());
    }

    @Test
    void anUnknownQidIngestsNothing() {
        when(wikidata.fetchPerson("Q42")).thenReturn(Optional.empty());

        IngestReport report = handler.handle(new IngestPeopleCommand(3000, "Q42"));

        assertThat(report.peopleSeen()).isZero();
        verifyNoInteractions(commons);
    }

    // --- helpers -------------------------------------------------------------

    private record CommonsFileAndInfo(CommonsFile file, MediaInfo info) {}

    private static BirthStatement birth() {
        return new BirthStatement("1950-03-10T00:00:00Z", 11, BirthDateSelector.GREGORIAN, BirthStatement.Rank.NORMAL);
    }

    private static PersonRecord person(String qid) {
        return new PersonRecord(qid, 50, "Jane", "Jeanne", Set.of("Jane main.jpg"), List.of(birth()), List.of());
    }

    private static Person stored(String qid, LocalDate birth, boolean excluded) {
        return Person.builder().id(UUID.randomUUID()).wikidataId(qid).birthDate(birth).excluded(excluded).build();
    }

    /** A JPEG dated July 14 of {@code year}, both in SDC and EXIF. */
    private static CommonsFileAndInfo photo(String title, int year) {
        String day = year + "-07-14";
        return new CommonsFileAndInfo(
                file(title, day, year + ":07:14 12:00:00"),
                new MediaInfo(List.of(PhotoDate.ofDay(LocalDate.parse(day))), 1));
    }

    private static CommonsFile file(String title, String informationDate, String exif) {
        return new CommonsFile(
                title,
                Math.abs(title.hashCode()),
                "image/jpeg",
                "https://upload.wikimedia.org/" + title,
                "https://commons.wikimedia.org/wiki/" + title,
                informationDate,
                exif,
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0",
                "<a href=\"https://commons.wikimedia.org/wiki/User:X\">Some Photographer</a>");
    }

    private void photos(String qid, CommonsFileAndInfo... photos) {
        Map<String, CommonsFile> files = new LinkedHashMap<>();
        Map<Long, MediaInfo> infos = new LinkedHashMap<>();
        for (CommonsFileAndInfo photo : photos) {
            files.put(photo.file().title(), photo.file());
            infos.put(photo.file().pageId(), photo.info());
        }
        List<String> titles = List.copyOf(files.keySet());
        when(commons.searchDepicting(qid)).thenReturn(titles);
        when(commons.fetchFiles(org.mockito.ArgumentMatchers.argThat(c -> c != null && c.containsAll(titles))))
                .thenReturn(files);
        when(commons.fetchMediaInfo(org.mockito.ArgumentMatchers.argThat(
                        ids -> ids != null && !ids.isEmpty() && ids.containsAll(infos.keySet()))))
                .thenReturn(infos);
    }

    private void noPhotos(String qid) {
        when(commons.searchDepicting(qid)).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<NewPhoto>> photosCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
