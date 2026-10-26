package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Uses qlever-selection.json: six chosen people covering the edge cases listed in its README. */
class WikidataClientTest {

    private final Map<String, PersonRecord> people = WikidataClient.group(
                    Fixtures.json("qlever-selection.json").path("results").path("bindings"))
            .stream()
            .collect(Collectors.toMap(PersonRecord::wikidataId, Function.identity()));

    @Test
    void groupsTheRowsOfEachPerson() {
        assertThat(people).containsOnlyKeys("Q1001", "Q1002250", "Q10068", "Q76745", "Q76984", "Q77017");
    }

    @Test
    void readsSitelinksAndBothLabels() {
        PersonRecord gandhi = people.get("Q1001");

        assertThat(gandhi.sitelinks()).isEqualTo(287);
        assertThat(gandhi.nameEn()).isEqualTo("Mahatma Gandhi");
        assertThat(gandhi.nameFr()).isEqualTo("Mohandas Karamchand Gandhi");
    }

    @Test
    void decodesTheFileNamesOfEveryMainImage() {
        assertThat(people.get("Q10068").mainImageFiles())
                .containsExactlyInAnyOrder(
                        "25th Laureus World Sports Awards - Lindsey Vonn - 240421 160319.jpg",
                        "2017 Audi FIS Ski Weltcup Garmisch-Partenkirchen Damen - Lindsey Vonn - by 2eight - 8SC8048.jpg");
        assertThat(people.get("Q1001").mainImageFiles()).containsExactly("Mahatma-Gandhi, studio, 1931.jpg");
    }

    @Test
    void keepsEachBirthStatementOnceWithItsRank() {
        assertThat(people.get("Q1002250").births())
                .containsExactlyInAnyOrder(
                        new BirthStatement("1951-11-14T00:00:00Z", 11, BirthDateSelector.GREGORIAN, BirthStatement.Rank.DEPRECATED),
                        new BirthStatement("1951-09-14T00:00:00Z", 11, BirthDateSelector.GREGORIAN, BirthStatement.Rank.NORMAL));
    }

    @Test
    void keepsEachDeathStatementOnceWithItsRank() {
        assertThat(people.get("Q76745").deaths())
                .containsExactlyInAnyOrder(
                        new DeathStatement("1882-01-12T00:00:00Z", 11, BirthStatement.Rank.NORMAL),
                        new DeathStatement("1882-01-11T00:00:00Z", 11, BirthStatement.Rank.PREFERRED));
        assertThat(people.get("Q1002250").deaths()).isEmpty();
    }

    @Test
    void theRecordedPeopleResolveToTheExpectedLifeDates() {
        assertThat(BirthDateSelector.select(people.get("Q1002250").births())).contains(LocalDate.of(1951, 9, 14));
        assertThat(DeathDateSelector.select(people.get("Q76745").deaths()))
                .isEqualTo(new DeathDateSelector.Died(LocalDate.of(1882, 1, 11)));
        // Jakob Böhme: the preferred birth date is in the Julian calendar.
        assertThat(BirthDateSelector.select(people.get("Q77017").births())).isEmpty();
    }

    @Test
    void postsThePagedSelectionQueryToQlever() {
        URI qlever = URI.create("https://qlever.example/api/wikidata");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikimediaHttp http = new WikimediaHttp(builder.build(), Fixtures.MAPPER, d -> {}, Duration.ZERO, 1);
        WikidataClient client = new WikidataClient(http, properties(qlever));
        server.expect(requestTo(qlever))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("LIMIT+500+OFFSET+1000")))
                .andRespond(withSuccess(Fixtures.text("qlever-selection.json"), MediaType.APPLICATION_JSON));

        List<PersonRecord> page = client.fetchPeople(1000, 500);

        assertThat(page).hasSize(6);
        server.verify();
    }

    @Test
    void anEmptyPageMeansTheEndOfTheList() {
        assertThat(WikidataClient.group(Fixtures.MAPPER.readTree("[]"))).isEmpty();
    }

    static WikimediaProperties properties(URI qlever) {
        return new WikimediaProperties("test-agent", qlever, URI.create("https://commons.example/w/api.php"), 0, 1, 1, 1);
    }
}
