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

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
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

    /** A sample larger than any test list: walks the plain ranking, stopping only at the target. */
    private static final int WHOLE_LIST = Integer.MAX_VALUE;
    private static final ImageReencoder.Reencoded IMAGE = new ImageReencoder.Reencoded(new byte[] {1}, 800, 1000);

    private final WikidataClient wikidata = mock(WikidataClient.class);
    private final CommonsClient commons = mock(CommonsClient.class);
    private final ImageDownloader downloader = mock(ImageDownloader.class);
    private final ImageReencoder reencoder = mock(ImageReencoder.class);
    private final PeopleStore store = mock(PeopleStore.class);
    private final IngestProperties properties = new IngestProperties(3000, 3, 5, 10, 50, 800, 5_000_000, 1930);
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
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(jane));
        photos("Q1", photo("File:Jane 1976.jpg", 1976), photo("File:Jane 1990.jpg", 1990));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

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
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(jane));
        when(commons.searchDepicting("Q1")).thenReturn(List.of("File:Other.jpg"));
        when(commons.fetchFiles(anyCollection())).thenReturn(Map.of());

        handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        verify(commons).fetchFiles(Set.of("File:Other.jpg", "File:Jane main.jpg"));
    }

    @Test
    void stopsOnceTheTargetIsReached() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1"), person("Q2"), person("Q3")));
        photos("Q1", photo("File:A 1976.jpg", 1976));
        photos("Q2", photo("File:B 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(2, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleKept()).isEqualTo(2);
        assertThat(report.peopleSeen()).isEqualTo(2);
        verify(commons, never()).searchDepicting("Q3");
        verify(wikidata, times(1)).fetchPeople(anyInt(), anyInt(), anyInt());
    }

    @Test
    void goesToTheNextPageUntilTheTargetIsReached() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1"), person("Q2"), person("Q3")));
        when(wikidata.fetchPeople(3, 3, 1930)).thenReturn(List.of(person("Q4")));
        photos("Q1", photo("File:A 1976.jpg", 1976));
        noPhotos("Q2");
        noPhotos("Q3");
        photos("Q4", photo("File:D 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(2, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleKept()).isEqualTo(2);
        assertThat(report.peopleSkipped()).containsEntry("NO_VALID_PHOTO", 2);
    }

    @Test
    void stopsWhenWikidataHasNoMorePeople() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        when(wikidata.fetchPeople(3, 3, 1930)).thenReturn(List.of());
        photos("Q1", photo("File:A 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(10, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleKept()).isEqualTo(1);
    }

    @Test
    void neverReingestsAnExcludedPerson() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")), List.of());
        when(store.find("Q1")).thenReturn(Optional.of(stored("Q1", BIRTH, true)));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleSkipped()).containsEntry("EXCLUDED", 1);
        verifyNoInteractions(commons);
        verify(store, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void skipsAPersonWithoutATrustworthyBirthDate() {
        PersonRecord yearOnly = new PersonRecord(
                "Q1", 50, "Jane", null, Set.of("Jane main.jpg"),
                List.of(new BirthStatement("1950-00-00T00:00:00Z", 9, BirthDateSelector.GREGORIAN, WikidataRank.NORMAL)),
                List.of(),
                null,
                null);
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(yearOnly), List.of());

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleSkipped()).containsEntry("BIRTH_DATE_UNUSABLE", 1);
        verifyNoInteractions(commons);
    }

    @Test
    void skipsAPersonWhoseDeathDateIsUnknown() {
        PersonRecord unknownDeath = new PersonRecord(
                "Q1", 50, "Jane", null, Set.of("Jane main.jpg"), List.of(birth()),
                List.of(new DeathStatement(null, null, WikidataRank.NORMAL)),
                null,
                null);
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(unknownDeath), List.of());

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleSkipped()).containsEntry("DEATH_DATE_UNUSABLE", 1);
    }

    @Test
    void passesTheDeathDateToTheRulesAndTheStore() {
        PersonRecord died = new PersonRecord(
                "Q1", 50, "Jane", null, Set.of("Jane main.jpg"), List.of(birth()),
                List.of(new DeathStatement("1985-01-01T00:00:00Z", 11, WikidataRank.NORMAL)),
                null,
                null);
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(died));
        photos("Q1", photo("File:Alive 1976.jpg", 1976), photo("File:Grave 1990.jpg", 1990));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(eq(died), eq(BIRTH), eq(LocalDate.of(1985, 1, 1)), saved.capture());
        assertThat(saved.getValue()).extracting(NewPhoto::commonsFile).containsExactly("Alive 1976.jpg");
        assertThat(report.photosRejected()).containsEntry("OUTSIDE_LIFETIME", 1);
    }

    @Test
    void skipsAPersonWithoutAnyName() {
        PersonRecord nameless = new PersonRecord(
                "Q1", 50, null, null, Set.of("X.jpg"), List.of(birth()), List.of(), null, null);
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(nameless), List.of());

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleSkipped()).containsEntry("NO_NAME", 1);
    }

    @Test
    void leavesAPersonAloneWhenWikidataChangedTheirBirthDate() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")), List.of());
        when(store.find("Q1")).thenReturn(Optional.of(stored("Q1", LocalDate.of(1951, 1, 1), false)));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleSkipped()).containsEntry("BIRTH_DATE_CHANGED", 1);
        verifyNoInteractions(commons);
        verify(store, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void doesNotStoreAPersonWithoutAnyValidPhoto() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")), List.of());
        photos("Q1", new CommonsFileAndInfo(file("File:Undated.jpg", null, null), MediaInfo.NONE));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleSkipped()).containsEntry("NO_VALID_PHOTO", 1);
        assertThat(report.photosRejected()).containsEntry("NOT_DATED", 1);
        verify(store, never()).save(any(), any(), any(), anyList());
    }

    @Test
    void countsPhotosDroppedByTheMinimumGap() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1980.jpg", 1980));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.photosAdded()).isEqualTo(1);
        assertThat(report.photosRejected()).containsEntry("NOT_SELECTED", 1);
    }

    @Test
    void aRerunKeepsStoredPhotosAndOnlyAddsWhatIsFarEnoughFromThem() {
        Person existing = stored("Q1", BIRTH, false);
        when(store.find("Q1")).thenReturn(Optional.of(existing));
        when(store.storedPhotos(existing.getId()))
                .thenReturn(new PeopleStore.StoredPhotos(List.of(new PhotoSelector.Kept(1976, 26)), Set.of("Old.jpg")));
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1990.jpg", 1990));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

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
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

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
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1977.jpg", 1977));

        handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(any(), any(), any(), saved.capture());
        // B is only one year away from the rejected A, which no longer blocks anything.
        assertThat(saved.getValue()).extracting(NewPhoto::commonsFile).containsExactly("B 1977.jpg");
    }

    @Test
    void oneFailingPersonDoesNotStopTheRun() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1"), person("Q2")));
        when(commons.searchDepicting("Q1")).thenThrow(new WikimediaUnavailableException("commons down"));
        photos("Q2", photo("File:B 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        assertThat(report.peopleSkipped()).containsEntry("FAILED", 1);
        assertThat(report.peopleKept()).isEqualTo(1);
    }

    @Test
    void aRejectedImageSkipsOnlyThatPhoto() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1990.jpg", 1990));
        when(downloader.download("https://upload.wikimedia.org/File:A 1976.jpg"))
                .thenThrow(new ImageRejectedException(ImageRejectedException.Reason.TOO_LARGE, "too big"));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

        ArgumentCaptor<List<NewPhoto>> saved = photosCaptor();
        verify(store).save(any(), any(), any(), saved.capture());
        assertThat(saved.getValue()).extracting(NewPhoto::commonsFile).containsExactly("B 1990.jpg");
        assertThat(report.photosRejected()).containsEntry("IMAGE_TOO_LARGE", 1);
    }

    @Test
    void storesTheAuthorAsPlainText() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976));

        handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, false));

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

        IngestReport report = handler.handle(new IngestPeopleCommand(3000, "Q42", 0, 0, false));

        assertThat(report.peopleKept()).isEqualTo(1);
        verify(wikidata, never()).fetchPeople(anyInt(), anyInt(), anyInt());
    }

    @Test
    void anUnknownQidIngestsNothing() {
        when(wikidata.fetchPerson("Q42")).thenReturn(Optional.empty());

        IngestReport report = handler.handle(new IngestPeopleCommand(3000, "Q42", 0, 0, false));

        assertThat(report.peopleSeen()).isZero();
        verifyNoInteractions(commons);
    }

    // --- helpers -------------------------------------------------------------

    @Test
    void startsFromTheGivenRankThenGoesOnPageByPage() {
        when(wikidata.fetchPeople(10000, 3, 1930)).thenReturn(List.of(person("Q1")));
        when(wikidata.fetchPeople(10003, 3, 1930)).thenReturn(List.of(person("Q2")));
        noPhotos("Q1");
        photos("Q2", photo("File:B 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 10000, WHOLE_LIST, false));

        assertThat(report.peopleKept()).isEqualTo(1);
        verify(wikidata, never()).fetchPeople(eq(0), anyInt(), anyInt());
    }

    @Test
    void aSampleStopsAfterThatManyPeopleSeenWhateverWasKept() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1"), person("Q2"), person("Q3")));
        noPhotos("Q1");
        noPhotos("Q2");

        IngestReport report = handler.handle(new IngestPeopleCommand(3000, null, 0, 2, false));

        assertThat(report.peopleSeen()).isEqualTo(2);
        assertThat(report.peopleKept()).isZero();
        verify(commons, never()).searchDepicting("Q3");
        verify(wikidata, times(1)).fetchPeople(anyInt(), anyInt(), anyInt());
    }

    @Test
    void aSampleStillStopsAtTheTargetFirst() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1"), person("Q2")));
        photos("Q1", photo("File:A 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, 2, false));

        assertThat(report.peopleSeen()).isEqualTo(1);
    }

    @Test
    void aDryRunAppliesEveryRuleButNeverTouchesTheStore() {
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:Teen 1966.jpg", 1966));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, true));

        verifyNoInteractions(store);
        verify(reencoder).reencode(any());
        assertThat(report.peopleKept()).isEqualTo(1);
        assertThat(report.photosAdded()).isEqualTo(1);
        assertThat(report.photosRejected()).containsEntry("MINOR", 1);
    }

    @Test
    void leansTowardTheAgeBandsThePoolLacksAcrossPeople() {
        // Stored: plenty of photos at 26-33. Each person has one at 26 and one at 34,
        // too close to keep both: the 34-41 one wins until that band catches up.
        when(store.photosByAgeBand()).thenReturn(Map.of("26-33", 2));
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1"), person("Q2"), person("Q3")));
        photos("Q1", photo("File:A1 1976.jpg", 1976), photo("File:A2 1984.jpg", 1984));
        photos("Q2", photo("File:B1 1976.jpg", 1976), photo("File:B2 1984.jpg", 1984));
        photos("Q3", photo("File:C1 1976.jpg", 1976), photo("File:C2 1984.jpg", 1984));

        IngestReport report = handler.handle(new IngestPeopleCommand(3, null, 0, WHOLE_LIST, false));

        // 26-33: 2 stored; 34-41 gets Q1 and Q2 (0 then 1 < 2), then ties at 2 and the
        // file name (C1 before C2) decides for Q3.
        assertThat(report.keptPhotosByAge()).isEqualTo(Map.of("34-41", 2, "26-33", 1));
    }

    @Test
    void aDryRunTreatsAnExcludedPersonAsNew() {
        // The store is not even read: the measure is about Wikidata and Commons alone.
        when(store.find("Q1")).thenReturn(Optional.of(stored("Q1", BIRTH, true)));
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1")));
        photos("Q1", photo("File:A 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(1, null, 0, WHOLE_LIST, true));

        assertThat(report.peopleKept()).isEqualTo(1);
    }

    @Test
    void countsPeopleSeenAndKeptPerBalanceCell() {
        PersonRecord unknown = new PersonRecord(
                "Q2", 50, "Sam", null, Set.of("Sam main.jpg"), List.of(birth()), List.of(), null, null);
        when(wikidata.fetchPeople(0, 3, 1930)).thenReturn(List.of(person("Q1"), unknown), List.of());
        photos("Q1", photo("File:A 1976.jpg", 1976), photo("File:B 1990.jpg", 1990));
        noPhotos("Q2");

        IngestReport report = handler.handle(new IngestPeopleCommand(5, null, 0, WHOLE_LIST, false));

        assertThat(report.seenByCell()).isEqualTo(Map.of("EUROPE/FEMALE", 1, "NONE/UNKNOWN", 1));
        assertThat(report.keptByCell()).isEqualTo(Map.of("EUROPE/FEMALE", 1));
        assertThat(report.imageBytes()).isEqualTo(2L * IMAGE.content().length);
        assertThat(report.toString())
                .contains("photos per kept person=2.00", "KB per image=0", "kept by cell={EUROPE/FEMALE=1}");
        // Born 1950: 26 in 1976, 40 in 1990.
        assertThat(report.validPhotosByAge()).isEqualTo(Map.of("26-33", 1, "34-41", 1));
        assertThat(report.keptPhotosByAge()).isEqualTo(Map.of("26-33", 1, "34-41", 1));
    }

    // Balanced ingestion. A target of 12 gives a quota of 1 per cell; cells not stubbed
    // have an empty ranking (the mock's default) and end up exhausted.

    private static final Cell EUROPE_WOMEN = new Cell(Continent.EUROPE, Gender.FEMALE);
    private static final Cell AFRICA_MEN = new Cell(Continent.AFRICA, Gender.MALE);
    private static final Cell ASIA_MEN = new Cell(Continent.ASIA, Gender.MALE);
    private static final Cell EUROPE_MEN = new Cell(Continent.EUROPE, Gender.MALE);

    @Test
    void theCellsTakeTurnsAndEachStopsAtItsQuota() {
        when(wikidata.fetchCell(EUROPE_WOMEN, 0, 3, 1930))
                .thenReturn(List.of(person("Q1", EUROPE_WOMEN), person("Q2", EUROPE_WOMEN)));
        when(wikidata.fetchCell(AFRICA_MEN, 0, 3, 1930)).thenReturn(List.of(person("Q3", AFRICA_MEN)));
        photos("Q1", photo("File:A 1976.jpg", 1976));
        photos("Q3", photo("File:C 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(12, null, 0, 0, false));

        assertThat(report.peopleKept()).isEqualTo(2);
        verify(commons, never()).searchDepicting("Q2");
        assertThat(report.balance())
                .containsEntry("EUROPE/FEMALE", "1/1")
                .containsEntry("AFRICA/MALE", "1/1")
                .containsEntry("OCEANIA/FEMALE", "0/1 exhausted")
                .hasSize(Cell.ALL.size());
        verify(wikidata, never()).fetchPeople(anyInt(), anyInt(), anyInt());
    }

    @Test
    void sharesTheTargetBetweenTheTwelveMaleAndFemaleCells() {
        when(wikidata.fetchCell(EUROPE_WOMEN, 0, 3, 1930))
                .thenReturn(List.of(person("Q1", EUROPE_WOMEN), person("Q2", EUROPE_WOMEN)));
        photos("Q1", photo("File:A 1976.jpg", 1976));
        photos("Q2", photo("File:B 1976.jpg", 1976));

        // 13 / 12 rounds up to 2.
        IngestReport report = handler.handle(new IngestPeopleCommand(13, null, 0, 0, false));

        assertThat(report.balance()).containsEntry("EUROPE/FEMALE", "2/2");
    }

    @Test
    void keepsSomeoneOnlyInTheCellTheirNationalitiesGiveThem() {
        // Ranked among European men for one nationality, but counted in Asia.
        PersonRecord twoNationalities = person("Q1", ASIA_MEN);
        when(wikidata.fetchCell(EUROPE_MEN, 0, 3, 1930)).thenReturn(List.of(twoNationalities));
        when(wikidata.fetchCell(ASIA_MEN, 0, 3, 1930)).thenReturn(List.of(twoNationalities));
        photos("Q1", photo("File:A 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(12, null, 0, 0, false));

        assertThat(report.peopleSeen()).isEqualTo(1);
        assertThat(report.balance())
                .containsEntry("ASIA/MALE", "1/1")
                .containsEntry("EUROPE/MALE", "0/1 exhausted");
    }

    @Test
    void goesToTheNextPageOfACellUntilItsQuotaIsReached() {
        when(wikidata.fetchCell(EUROPE_WOMEN, 0, 3, 1930))
                .thenReturn(List.of(person("Q1", EUROPE_WOMEN), person("Q2", EUROPE_WOMEN), person("Q3", EUROPE_WOMEN)));
        when(wikidata.fetchCell(EUROPE_WOMEN, 3, 3, 1930)).thenReturn(List.of(person("Q4", EUROPE_WOMEN)));
        noPhotos("Q1");
        noPhotos("Q2");
        noPhotos("Q3");
        photos("Q4", photo("File:D 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(12, null, 0, 0, false));

        assertThat(report.balance()).containsEntry("EUROPE/FEMALE", "1/1");
    }

    @Test
    void aRerunCountsThePeopleAlreadyStoredInEachCell() {
        when(store.balancedPeople()).thenReturn(Map.of(EUROPE_WOMEN, Set.of("Q9")));

        IngestReport report = handler.handle(new IngestPeopleCommand(12, null, 0, 0, false));

        verify(wikidata, never()).fetchCell(eq(EUROPE_WOMEN), anyInt(), anyInt(), anyInt());
        assertThat(report.balance()).containsEntry("EUROPE/FEMALE", "1/1");
    }

    @Test
    void aStoredPersonMetAgainIsRefreshedWithoutCountingTwice() {
        when(store.balancedPeople()).thenReturn(Map.of(EUROPE_WOMEN, Set.of("Q1")));
        when(wikidata.fetchCell(EUROPE_WOMEN, 0, 3, 1930))
                .thenReturn(List.of(person("Q1", EUROPE_WOMEN), person("Q2", EUROPE_WOMEN)));
        photos("Q1", photo("File:A 1976.jpg", 1976));
        photos("Q2", photo("File:B 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(24, null, 0, 0, false));

        assertThat(report.balance()).containsEntry("EUROPE/FEMALE", "2/2");
        assertThat(report.peopleKept()).isEqualTo(2);
    }

    @Test
    void aPersonThatFailsDoesNotCountInTheirCell() {
        when(wikidata.fetchCell(EUROPE_WOMEN, 0, 3, 1930)).thenReturn(List.of(person("Q1", EUROPE_WOMEN)));
        when(commons.searchDepicting("Q1")).thenThrow(new WikimediaUnavailableException("down"));

        IngestReport report = handler.handle(new IngestPeopleCommand(12, null, 0, 0, false));

        assertThat(report.balance()).containsEntry("EUROPE/FEMALE", "0/1 exhausted");
    }

    @Test
    void aBalancedDryRunNeverTouchesTheStore() {
        when(wikidata.fetchCell(EUROPE_WOMEN, 0, 3, 1930)).thenReturn(List.of(person("Q1", EUROPE_WOMEN)));
        photos("Q1", photo("File:A 1976.jpg", 1976));

        IngestReport report = handler.handle(new IngestPeopleCommand(12, null, 0, 0, true));

        verifyNoInteractions(store);
        assertThat(report.balance()).containsEntry("EUROPE/FEMALE", "1/1");
    }

    private record CommonsFileAndInfo(CommonsFile file, MediaInfo info) {}

    private static BirthStatement birth() {
        return new BirthStatement("1950-03-10T00:00:00Z", 11, BirthDateSelector.GREGORIAN, WikidataRank.NORMAL);
    }

    private static PersonRecord person(String qid) {
        return new PersonRecord(
                qid, 50, "Jane", "Jeanne", Set.of("Jane main.jpg"), List.of(birth()), List.of(), Gender.FEMALE,
                Continent.EUROPE);
    }

    private static PersonRecord person(String qid, Cell cell) {
        return new PersonRecord(
                qid, 50, "Jane", "Jeanne", Set.of(qid + " main.jpg"), List.of(birth()), List.of(), cell.gender(),
                cell.continent());
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
