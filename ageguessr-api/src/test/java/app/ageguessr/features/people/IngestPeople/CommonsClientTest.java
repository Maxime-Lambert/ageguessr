package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class CommonsClientTest {

    private static final String API = "https://commons.example/w/api.php";

    private MockRestServiceServer server;
    private CommonsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WikimediaHttp http = new WikimediaHttp(builder.build(), Fixtures.MAPPER, d -> {}, Duration.ZERO, 1);
        WikimediaProperties properties = new WikimediaProperties(
                "test-agent", URI.create("https://qlever.example"), URI.create(API), 0, 1, 1, 1);
        client = new CommonsClient(http, properties, new IngestProperties(3000, 500, 5, 10, 50, 800, 5_000_000));
    }

    @Test
    void searchesBitmapsDepictingThePerson() {
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(queryParam("srsearch", "haswbstatement:P180%3DQ262665%20filetype:bitmap"))
                .andExpect(queryParam("srlimit", "50"))
                .andExpect(queryParam("maxlag", "5"))
                .andRespond(withSuccess(Fixtures.text("commons-search-Q262665.json"), MediaType.APPLICATION_JSON));

        List<String> titles = client.searchDepicting("Q262665");

        assertThat(titles).hasSize(26).allMatch(t -> t.startsWith("File:"));
        server.verify();
    }

    @Test
    void readsEveryFieldOfAFile() {
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("maxlag", "5"))
                .andExpect(content().formDataContains(Map.of(
                        "iiurlwidth", "800", "titles", "File:ROMY2011 a21 Mallika Sherawat.jpg")))
                .andRespond(withSuccess(Fixtures.text("commons-imageinfo-Q262665.json"), MediaType.APPLICATION_JSON));

        Map<String, CommonsFile> files = client.fetchFiles(List.of("File:ROMY2011 a21 Mallika Sherawat.jpg"));

        CommonsFile romy = files.get("File:ROMY2011 a21 Mallika Sherawat.jpg");
        assertThat(files).hasSize(26);
        assertThat(romy.pageId()).isEqualTo(14956440L);
        assertThat(romy.mime()).isEqualTo("image/jpeg");
        assertThat(romy.thumbnailUrl()).startsWith("https://upload.wikimedia.org/");
        assertThat(romy.descriptionUrl()).isEqualTo("https://commons.wikimedia.org/wiki/File:ROMY2011_a21_Mallika_Sherawat.jpg");
        assertThat(romy.exifDateTimeOriginal()).isEqualTo("2011:04:16 19:34:52");
        assertThat(romy.licenseShortName()).isEqualTo("CC BY-SA 3.0");
        assertThat(romy.licenseUrl()).isNotBlank();
        assertThat(romy.artistHtml()).isNotBlank();
    }

    @Test
    void neverTakesTheSoftwareDateTimeForTheCameraDate() {
        // Ferdinand Hurter.jpg has EXIF "DateTime" but no "DateTimeOriginal".
        CommonsFile hurter = CommonsClient.parseFile(Fixtures.json("commons-imageinfo-Q123344.json")
                        .path("query").path("pages").get(0))
                .orElseThrow();

        assertThat(hurter.exifDateTimeOriginal()).isNull();
    }

    @Test
    void toleratesMetadataReportedAsAnError() {
        // In the fixture, the first file's metadata is [{"name": "_error", "value": "0"}].
        CommonsFile first = CommonsClient.parseFile(Fixtures.json("commons-imageinfo-Q262665.json")
                        .path("query").path("pages").get(0))
                .orElseThrow();

        assertThat(first.exifDateTimeOriginal()).isNull();
    }

    @Test
    void skipsMissingFiles() {
        JsonNode missing = Fixtures.MAPPER.readTree("{\"ns\": 6, \"title\": \"File:Gone.jpg\", \"missing\": true}");

        assertThat(CommonsClient.parseFile(missing)).isEmpty();
    }

    @Test
    void sendsTitlesTooLongForAUrlInTheBody() {
        String longTitle = "File:" + "Very long file name about a press conference ".repeat(10) + ".jpg";
        server.expect(requestTo(API + "?format=json&formatversion=2&maxlag=5"))
                .andRespond(withSuccess("{\"query\": {\"pages\": []}}", MediaType.APPLICATION_JSON));

        client.fetchFiles(IntStream.range(0, 50).mapToObj(i -> longTitle + i).toList());

        server.verify();
    }

    @Test
    void asksForFiftyTitlesAtMostPerRequest() {
        server.expect(times(2), requestTo(Matchers.startsWith(API)))
                .andRespond(withSuccess("{\"query\": {\"pages\": []}}", MediaType.APPLICATION_JSON));

        client.fetchFiles(IntStream.range(0, 51).mapToObj(i -> "File:" + i + ".jpg").toList());

        server.verify();
    }

    @Test
    void fiftyTitlesFitInOneRequest() {
        server.expect(times(1), requestTo(Matchers.startsWith(API)))
                .andRespond(withSuccess("{\"query\": {\"pages\": []}}", MediaType.APPLICATION_JSON));

        client.fetchFiles(IntStream.range(0, 50).mapToObj(i -> "File:" + i + ".jpg").toList());

        server.verify();
    }

    @Test
    void readsInceptionDatesAndCountsDepictedSubjects() {
        server.expect(requestTo(Matchers.startsWith(API)))
                .andExpect(queryParam("action", "wbgetentities"))
                .andExpect(queryParam("ids", "M14956440%7CM14956453"))
                .andRespond(withSuccess(Fixtures.text("commons-mediainfo-Q262665.json"), MediaType.APPLICATION_JSON));

        Map<Long, MediaInfo> infos = client.fetchMediaInfo(List.of(14956440L, 14956453L));

        assertThat(infos.get(14956440L))
                .isEqualTo(new MediaInfo(List.of(PhotoDate.ofDay(LocalDate.of(2011, 4, 16))), 1));
        assertThat(infos.get(14956453L).depictedCount()).isEqualTo(3);
        // "Mallika Sherawat Cannes 2014 2.jpg" has statements, but no P571.
        assertThat(infos.get(33157215L).inceptions()).isEmpty();
    }

    @Test
    void anEntityWithoutStatementsIsSerializedAsAnEmptyArray() {
        JsonNode entity = Fixtures.MAPPER.readTree("{\"id\": \"M1\", \"statements\": []}");

        assertThat(CommonsClient.parseMediaInfo(entity)).isEqualTo(MediaInfo.NONE);
    }

    @Test
    void ignoresDeprecatedAndNonGregorianInceptionsAndDeprecatedDepictions() {
        JsonNode entity = Fixtures.MAPPER.readTree("""
                {"id": "M1", "statements": {
                  "P571": [
                    {"rank": "deprecated", "mainsnak": {"datavalue": {"value": {"time": "+1990-01-01T00:00:00Z", "precision": 11, "calendarmodel": "http://www.wikidata.org/entity/Q1985727"}}}},
                    {"rank": "normal", "mainsnak": {"datavalue": {"value": {"time": "+1991-01-01T00:00:00Z", "precision": 11, "calendarmodel": "http://www.wikidata.org/entity/Q1985786"}}}},
                    {"rank": "normal", "mainsnak": {"snaktype": "somevalue"}},
                    {"rank": "normal", "mainsnak": {"datavalue": {"value": {"time": "+1992-05-00T00:00:00Z", "precision": 10, "calendarmodel": "http://www.wikidata.org/entity/Q1985727"}}}}
                  ],
                  "P180": [
                    {"rank": "normal", "mainsnak": {}},
                    {"rank": "deprecated", "mainsnak": {}}
                  ]
                }}
                """);

        assertThat(CommonsClient.parseMediaInfo(entity))
                .isEqualTo(new MediaInfo(List.of(PhotoDate.ofMonth(java.time.YearMonth.of(1992, 5))), 1));
    }
}
