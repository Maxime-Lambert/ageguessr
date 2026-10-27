package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
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
import tools.jackson.databind.JsonNode;

/**
 * Uses the qlever-*.json fixtures: seven chosen people covering the edge cases listed in
 * src/test/resources/people/README.md.
 */
class WikidataClientTest {

    private static final URI QLEVER = URI.create("https://qlever.example/api/wikidata");

    private final Map<String, PersonRecord> people = WikidataClient.assemble(
                    bindings("qlever-page.json"),
                    bindings("qlever-births.json"),
                    bindings("qlever-deaths.json"),
                    bindings("qlever-images.json"),
                    bindings("qlever-labels.json"),
                    bindings("qlever-profiles.json"))
            .stream()
            .collect(Collectors.toMap(PersonRecord::wikidataId, Function.identity()));

    @Test
    void assemblesEveryPersonOfThePage() {
        assertThat(people)
                .containsOnlyKeys("Q1001", "Q1002250", "Q10068", "Q76745", "Q76984", "Q77017", "Q11571");
    }

    @Test
    void keepsTheRankingOrder() {
        List<PersonRecord> ordered = WikidataClient.assemble(
                bindings("qlever-page.json"), empty(), empty(), empty(), empty(), empty());

        assertThat(ordered).extracting(PersonRecord::sitelinks).isSortedAccordingTo((a, b) -> b - a);
    }

    @Test
    void readsSitelinksAndBothLabels() {
        PersonRecord gandhi = people.get("Q1001");

        assertThat(gandhi.sitelinks()).isEqualTo(287);
        assertThat(gandhi.nameEn()).isEqualTo("Mahatma Gandhi");
        assertThat(gandhi.nameFr()).isEqualTo("Mohandas Karamchand Gandhi");
    }

    @Test
    void findsTheEnglishLabelAnExactLanguageFilterMissed() {
        // Cristiano Ronaldo has en, en-gb and en-us labels; FILTER(LANG(?l) = "en") found none.
        assertThat(people.get("Q11571").nameEn()).isEqualTo("Cristiano Ronaldo");
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
    void keepsEachBirthStatementWithItsRank() {
        assertThat(people.get("Q1002250").births())
                .containsExactlyInAnyOrder(
                        new BirthStatement("1951-11-14T00:00:00Z", 11, BirthDateSelector.GREGORIAN, WikidataRank.DEPRECATED),
                        new BirthStatement("1951-09-14T00:00:00Z", 11, BirthDateSelector.GREGORIAN, WikidataRank.NORMAL));
    }

    @Test
    void keepsEachDeathStatementWithItsRank() {
        assertThat(people.get("Q76745").deaths())
                .containsExactlyInAnyOrder(
                        new DeathStatement("1882-01-12T00:00:00Z", 11, WikidataRank.NORMAL),
                        new DeathStatement("1882-01-11T00:00:00Z", 11, WikidataRank.PREFERRED));
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
    void readsTheGenderAndTheContinentOfNationality() {
        assertThat(people.get("Q10068"))
                .extracting(PersonRecord::gender, PersonRecord::continent)
                .containsExactly(Gender.FEMALE, Continent.NORTH_AMERICA);
        // Gandhi: British India, then India, both in Asia.
        assertThat(people.get("Q1001"))
                .extracting(PersonRecord::gender, PersonRecord::continent)
                .containsExactly(Gender.MALE, Continent.ASIA);
    }

    @Test
    void leavesGenderAndContinentUnknownWithoutAnyProfileRow() {
        PersonRecord person = WikidataClient.assemble(page("Q1"), empty(), empty(), empty(), empty(), empty())
                .getFirst();

        assertThat(person.gender()).isNull();
        assertThat(person.continent()).isNull();
    }

    @Test
    void ignoresAProfileValueThatIsNotAWikidataEntity() {
        JsonNode profiles = Fixtures.MAPPER.readTree("""
                [{"person": {"value": "http://www.wikidata.org/entity/Q1"}, "gender": {"value": "_:blank"}},
                 {"person": {"value": "http://www.wikidata.org/entity/Q1"},
                  "gender": {"value": "http://www.wikidata.org/entity/"}},
                 {"person": {"value": "http://www.wikidata.org/entity/Q1"},
                  "continent": {"value": "http://www.wikidata.org/entity/Q46"}}]
                """);

        PersonRecord person = WikidataClient.assemble(page("Q1"), empty(), empty(), empty(), empty(), profiles)
                .getFirst();

        assertThat(person.gender()).isNull();
        assertThat(person.continent()).isEqualTo(Continent.EUROPE);
    }

    @Test
    void readsAnUnknownDeathDateAsAStatementWithoutValue() {
        JsonNode deaths = Fixtures.MAPPER.readTree("""
                [{"person": {"value": "http://www.wikidata.org/entity/Q1"},
                  "statement": {"value": "s1"},
                  "rank": {"value": "http://wikiba.se/ontology#NormalRank"}}]
                """);

        PersonRecord person = WikidataClient.assemble(page("Q1"), empty(), deaths, empty(), empty(), empty()).getFirst();

        assertThat(person.deaths()).containsExactly(new DeathStatement(null, null, WikidataRank.NORMAL));
    }

    @Test
    void prefersTheExactLanguageOverARegionalVariantWhateverTheRowOrder() {
        JsonNode labels = labels("Jane (UK)", "en-gb", "Jeanne (Canada)", "fr-ca", "Jane", "en", "Jeanne", "fr");

        PersonRecord person = WikidataClient.assemble(page("Q1"), empty(), empty(), empty(), labels, empty()).getFirst();

        assertThat(person.nameEn()).isEqualTo("Jane");
        assertThat(person.nameFr()).isEqualTo("Jeanne");
    }

    @Test
    void fallsBackToARegionalVariantWhenTheExactLanguageIsMissing() {
        JsonNode labels = labels("Jane (US)", "en-us");

        PersonRecord person = WikidataClient.assemble(page("Q1"), empty(), empty(), empty(), labels, empty()).getFirst();

        assertThat(person.nameEn()).isEqualTo("Jane (US)");
        assertThat(person.nameFr()).isNull();
    }

    @Test
    void fallsBackToTheMultipleLanguagesLabelForBothNames() {
        // Douglas Adams since Wikidata introduced "mul": no "en" label left.
        JsonNode labels = labels("Douglas Adams", "mul", "Duglassius Adams", "la");

        PersonRecord person = WikidataClient.assemble(page("Q1"), empty(), empty(), empty(), labels, empty())
                .getFirst();

        assertThat(person.nameEn()).isEqualTo("Douglas Adams");
        assertThat(person.nameFr()).isEqualTo("Douglas Adams");
    }

    @Test
    void prefersARegionalVariantOverTheMultipleLanguagesLabelWhateverTheRowOrder() {
        JsonNode labels = labels("Jane (US)", "en-us", "Jane", "mul", "Jeanne (Canada)", "fr-ca");

        PersonRecord person = WikidataClient.assemble(page("Q1"), empty(), empty(), empty(), labels, empty())
                .getFirst();

        assertThat(person.nameEn()).isEqualTo("Jane (US)");
        assertThat(person.nameFr()).isEqualTo("Jeanne (Canada)");
    }

    @Test
    void ignoresRowsAboutPeopleOutsideThePage() {
        JsonNode labels = Fixtures.MAPPER.readTree("""
                [{"person": {"value": "http://www.wikidata.org/entity/Q999"},
                  "label": {"value": "Stranger"}, "lang": {"value": "en"}}]
                """);

        assertThat(WikidataClient.assemble(page("Q1"), empty(), empty(), empty(), labels, empty()))
                .singleElement()
                .extracting(PersonRecord::nameEn)
                .isNull();
    }

    @Test
    void skipsRowsWhoseQidIsNotWellFormed() {
        JsonNode ranking = Fixtures.MAPPER.readTree("""
                [{"person": {"value": "http://www.wikidata.org/entity/Q1 } ?x ?y ?z"}, "sitelinks": {"value": "9"}},
                 {"person": {"value": "https://evil.example/entity/Q2"}, "sitelinks": {"value": "8"}},
                 {"person": {"value": "http://www.wikidata.org/entity/Q3"}, "sitelinks": {"value": "7"}}]
                """);

        assertThat(WikidataClient.assemble(ranking, empty(), empty(), empty(), empty(), empty()))
                .extracting(PersonRecord::wikidataId)
                .containsExactly("Q3");
    }

    @Test
    void neverPutsAMalformedQidFromQleverIntoADetailQuery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(QLEVER))
                .andRespond(withSuccess("""
                        {"results": {"bindings": [
                          {"person": {"value": "http://www.wikidata.org/entity/Q1 } DROP ALL"}, "sitelinks": {"value": "9"}}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(never(), requestTo(QLEVER));
        WikidataClient client = new WikidataClient(
                Fixtures.http(builder.build(), d -> {}, Duration.ZERO, 1), properties());

        assertThat(client.fetchPeople(0, 500, 1930)).isEmpty();
        server.verify();
    }

    @Test
    void fetchesAPageThenItsDetailsRestrictedToThePageQids() {
        MockRestServiceServer server = expectSixPosts("LIMIT+500+OFFSET+1000");

        List<PersonRecord> page = client(server).fetchPeople(1000, 500, 1930);

        assertThat(page).hasSize(7);
        server.verify();
    }

    @Test
    void ranksOnlyPeopleBornFromTheGivenYear() {
        MockRestServiceServer server = expectSixPosts("FILTER%28YEAR%28%3Fbirth%29+%3E%3D+1930%29");

        client(server).fetchPeople(0, 500, 1930);

        server.verify();
    }

    @Test
    void ranksOneCellByItsContinentsAndGenderValues() {
        // Asia folds in Eurasia and the Indian subcontinent; women include trans women.
        MockRestServiceServer server = expectSixPosts(
                "VALUES+%3Fcontinent+%7B+wd%3AQ48+wd%3AQ5401+wd%3AQ60140+%7D");

        List<PersonRecord> page = client(server).fetchCell(new Cell(Continent.ASIA, Gender.FEMALE), 500, 500, 1930);

        assertThat(page).hasSize(7);
        server.verify();
    }

    @Test
    void ranksTheWomenOfACellWithEveryValueMeaningWoman() {
        MockRestServiceServer server =
                expectSixPosts("VALUES+%3Fgender+%7B+wd%3AQ1052281+wd%3AQ15145779+wd%3AQ6581072+%7D");

        client(server).fetchCell(new Cell(Continent.EUROPE, Gender.FEMALE), 0, 500, 1930);

        server.verify();
    }

    @Test
    void ranksTheOtherCellAsAnyGenderButMaleOrFemale() {
        MockRestServiceServer server = expectSixPosts("FILTER%28%3Fgender+NOT+IN+%28wd%3AQ1052281%2C+wd%3AQ15145778");

        client(server).fetchCell(new Cell(Continent.OCEANIA, Gender.OTHER), 0, 500, 1930);

        server.verify();
    }

    @Test
    void anEmptyPageMeansTheEndOfTheListWithoutAnyDetailQuery() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(QLEVER)).andRespond(withSuccess(emptyResult(), MediaType.APPLICATION_JSON));
        server.expect(never(), requestTo(QLEVER));
        WikidataClient client = new WikidataClient(
                Fixtures.http(builder.build(), d -> {}, Duration.ZERO, 1), properties());

        assertThat(client.fetchPeople(0, 500, 1930)).isEmpty();
        server.verify();
    }

    @Test
    void fetchesOnePersonByQid() {
        MockRestServiceServer server = expectSixPosts("VALUES+%3Fperson+%7B+wd%3AQ1001+%7D");

        assertThat(client(server).fetchPerson("Q1001")).isPresent();
        server.verify();
    }

    @Test
    void refusesSomethingThatIsNotAQidBeforeBuildingTheQuery() {
        WikidataClient client = new WikidataClient(
                Fixtures.http(RestClient.builder().build(), d -> {}, Duration.ZERO, 1), properties());

        for (String bad : new String[] {"Q0", "42", "Q42 } ?x ?y ?z", "q42", "Q1234567890123"}) {
            assertThatThrownBy(() -> client.fetchPerson(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    static WikimediaProperties properties() {
        return new WikimediaProperties("test-agent", QLEVER, URI.create("https://commons.example/w/api.php"), 0, 1, 1, 1);
    }

    private MockRestServiceServer server;

    private MockRestServiceServer expectSixPosts(String firstQueryMarker) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        this.builder = builder;
        server.expect(requestTo(QLEVER))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString(firstQueryMarker)))
                .andRespond(withSuccess(Fixtures.text("qlever-page.json"), MediaType.APPLICATION_JSON));
        for (String[] detail : new String[][] {
            {"p%3AP569", "qlever-births.json"},
            {"p%3AP570", "qlever-deaths.json"},
            {"wdt%3AP18", "qlever-images.json"},
            // The labels query, which must ask for "mul" labels too.
            {"%3Flang+%3D+%22mul%22", "qlever-labels.json"},
            {"wdt%3AP21", "qlever-profiles.json"}
        }) {
            server.expect(requestTo(QLEVER))
                    .andExpect(content().string(Matchers.allOf(
                            Matchers.containsString(detail[0]), Matchers.containsString("wd%3AQ11571"))))
                    .andRespond(withSuccess(Fixtures.text(detail[1]), MediaType.APPLICATION_JSON));
        }
        return server;
    }

    private RestClient.Builder builder;

    private WikidataClient client(MockRestServiceServer server) {
        return new WikidataClient(
                Fixtures.http(builder.build(), d -> {}, Duration.ZERO, 1), properties());
    }

    private static JsonNode bindings(String fixture) {
        return Fixtures.json(fixture).path("results").path("bindings");
    }

    private static JsonNode empty() {
        return Fixtures.MAPPER.readTree("[]");
    }

    private static String emptyResult() {
        return "{\"results\": {\"bindings\": []}}";
    }

    private static JsonNode page(String qid) {
        return Fixtures.MAPPER.readTree("""
                [{"person": {"value": "http://www.wikidata.org/entity/%s"}, "sitelinks": {"value": "10"}}]
                """.formatted(qid));
    }

    /** Pairs of (label, language) for person Q1. */
    private static JsonNode labels(String... labelAndLang) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < labelAndLang.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            json.append("""
                    {"person": {"value": "http://www.wikidata.org/entity/Q1"},
                     "label": {"value": "%s"}, "lang": {"value": "%s"}}""".formatted(labelAndLang[i], labelAndLang[i + 1]));
        }
        return Fixtures.MAPPER.readTree(json.append(']').toString());
    }
}
