package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.WikidataId;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/**
 * Lists people from Wikidata, most notable first, through QLever: the public Wikidata
 * SPARQL service times out on the ranking (plans/done/people-sourcing.md, Phase 1).
 *
 * <p>One small query per kind of data, assembled in Java. A single query joining the
 * ranking, birth and death statements and labels ran close to QLever's 30 s limit, then
 * past it (QLever answers a timeout with HTTP 429). Separately, a page of 500 people
 * takes ~7 s: ranking ~4 s, then four queries restricted to the page's QIDs (VALUES) of
 * about a second each.
 */
@Component
class WikidataClient {

    private static final String ENTITY_PREFIX = "http://www.wikidata.org/entity/";
    private static final String FILE_PATH_MARKER = "Special:FilePath/";
    private static final String RANK_PREFIX = "http://wikiba.se/ontology#";

    private static final String PREFIXES = """
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX p: <http://www.wikidata.org/prop/>
            PREFIX psv: <http://www.wikidata.org/prop/statement/value/>
            PREFIX wikibase: <http://wikiba.se/ontology#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            """;

    // The QID breaks sitelinks ties, so that pages never overlap.
    private static final String PAGE = """
            SELECT DISTINCT ?person ?sitelinks WHERE {
              ?person wdt:P31 wd:Q5 ; wdt:P18 ?image ; wikibase:sitelinks ?sitelinks .
            }
            ORDER BY DESC(?sitelinks) ?person
            LIMIT %d OFFSET %d
            """;

    private static final String ONE_PERSON = """
            SELECT ?person ?sitelinks WHERE {
              VALUES ?person { wd:%s }
              ?person wdt:P31 wd:Q5 ; wdt:P18 ?image ; wikibase:sitelinks ?sitelinks .
            }
            LIMIT 1
            """;

    private static final String BIRTHS = """
            SELECT ?person ?birth ?precision ?calendar ?rank WHERE {
              VALUES ?person { %s }
              ?person p:P569 ?statement .
              ?statement wikibase:rank ?rank ; psv:P569 ?value .
              ?value wikibase:timeValue ?birth ;
                     wikibase:timePrecision ?precision ;
                     wikibase:timeCalendarModel ?calendar .
            }
            """;

    // OPTIONAL value: an "unknown value" death statement has a rank but no date.
    private static final String DEATHS = """
            SELECT ?person ?statement ?rank ?death ?precision WHERE {
              VALUES ?person { %s }
              ?person p:P570 ?statement .
              ?statement wikibase:rank ?rank .
              OPTIONAL {
                ?statement psv:P570 ?value .
                ?value wikibase:timeValue ?death ; wikibase:timePrecision ?precision .
              }
            }
            """;

    private static final String IMAGES = """
            SELECT ?person ?image WHERE {
              VALUES ?person { %s }
              ?person wdt:P18 ?image .
            }
            """;

    // Every "en*" / "fr*" label with its language: the exact language is preferred in
    // Java. On QLever, FILTER(LANG(?l) = "en") misses some English labels (Cristiano
    // Ronaldo, seen on a real run) that STRSTARTS finds.
    private static final String LABELS = """
            SELECT ?person ?label ?lang WHERE {
              VALUES ?person { %s }
              ?person rdfs:label ?label .
              BIND(LANG(?label) AS ?lang)
              FILTER(STRSTARTS(?lang, "en") || STRSTARTS(?lang, "fr"))
            }
            """;

    private final WikimediaHttp http;
    private final WikimediaProperties properties;

    WikidataClient(WikimediaHttp http, WikimediaProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    /** People ranked {@code offset} to {@code offset + limit - 1} by notoriety. Fewer (or none) past the end. */
    List<PersonRecord> fetchPeople(int offset, int limit) {
        return details(query(PAGE.formatted(limit, offset)));
    }

    /** One person by QID, to (re)ingest them alone. Empty if they are not a human with a P18 image. */
    Optional<PersonRecord> fetchPerson(String wikidataId) {
        // Checked before it reaches the query text: the QID comes from the command line.
        if (!WikidataId.isValid(wikidataId)) {
            throw new IllegalArgumentException("not a Wikidata QID: " + wikidataId);
        }
        return details(query(ONE_PERSON.formatted(wikidataId))).stream().findFirst();
    }

    private List<PersonRecord> details(JsonNode ranking) {
        Set<String> qids = new LinkedHashSet<>();
        for (JsonNode row : ranking) {
            String qid = qid(row);
            if (qid != null) {
                qids.add(qid);
            }
        }
        if (qids.isEmpty()) {
            return List.of();
        }
        String values = qids.stream().map(q -> "wd:" + q).collect(Collectors.joining(" "));
        return assemble(
                ranking,
                query(BIRTHS.formatted(values)),
                query(DEATHS.formatted(values)),
                query(IMAGES.formatted(values)),
                query(LABELS.formatted(values)));
    }

    /** Builds the people of a page from the five result sets, in ranking order. */
    static List<PersonRecord> assemble(
            JsonNode ranking, JsonNode births, JsonNode deaths, JsonNode images, JsonNode labels) {
        Map<String, Builder> people = new LinkedHashMap<>();
        for (JsonNode row : ranking) {
            String qid = qid(row);
            if (qid != null) {
                people.put(qid, new Builder(qid, Integer.parseInt(value(row, "sitelinks"))));
            }
        }
        for (JsonNode row : births) {
            Builder person = people.get(qid(row));
            if (person != null) {
                person.births.add(new BirthStatement(
                        value(row, "birth"),
                        Integer.parseInt(value(row, "precision")),
                        value(row, "calendar"),
                        rank(value(row, "rank"))));
            }
        }
        for (JsonNode row : deaths) {
            Builder person = people.get(qid(row));
            if (person != null) {
                String precision = value(row, "precision");
                person.deaths.put(value(row, "statement"), new DeathStatement(
                        value(row, "death"),
                        precision == null ? null : Integer.valueOf(precision),
                        rank(value(row, "rank"))));
            }
        }
        for (JsonNode row : images) {
            Builder person = people.get(qid(row));
            String image = value(row, "image");
            if (person != null && image != null && image.contains(FILE_PATH_MARKER)) {
                String encoded = image.substring(image.indexOf(FILE_PATH_MARKER) + FILE_PATH_MARKER.length());
                person.images.add(URLDecoder.decode(encoded, StandardCharsets.UTF_8));
            }
        }
        for (JsonNode row : labels) {
            Builder person = people.get(qid(row));
            if (person != null) {
                person.nameEn.offer(value(row, "label"), value(row, "lang"), "en");
                person.nameFr.offer(value(row, "label"), value(row, "lang"), "fr");
            }
        }
        return people.values().stream().map(Builder::build).toList();
    }

    private JsonNode query(String sparql) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", PREFIXES + sparql);
        return http.postForm(properties.qleverUrl(), form).path("results").path("bindings");
    }

    /**
     * The row's QID, or null if it is not a well-formed one. QIDs from the ranking go
     * back into the detail queries' text (VALUES): each one is checked like the
     * command-line --qid, so a malformed answer can never alter a query.
     */
    private static String qid(JsonNode row) {
        String uri = value(row, "person");
        if (uri == null || !uri.startsWith(ENTITY_PREFIX)) {
            return null;
        }
        String id = uri.substring(ENTITY_PREFIX.length());
        return WikidataId.isValid(id) ? id : null;
    }

    private static String value(JsonNode row, String variable) {
        JsonNode node = row.path(variable).path("value");
        return node.isMissingNode() ? null : node.asString();
    }

    private static WikidataRank rank(String uri) {
        String name = uri.startsWith(RANK_PREFIX) ? uri.substring(RANK_PREFIX.length()) : uri;
        return switch (name) {
            case "PreferredRank" -> WikidataRank.PREFERRED;
            case "DeprecatedRank" -> WikidataRank.DEPRECATED;
            default -> WikidataRank.NORMAL;
        };
    }

    /** Keeps the label of the exact language if there is one, else the first regional variant (en-gb...). */
    private static final class Label {
        private String text;
        private boolean exact;

        void offer(String candidate, String lang, String wanted) {
            if (candidate == null || lang == null || exact || !lang.startsWith(wanted)) {
                return;
            }
            if (wanted.equals(lang)) {
                text = candidate;
                exact = true;
            } else if (text == null) {
                text = candidate;
            }
        }
    }

    private static final class Builder {
        private final String qid;
        private final int sitelinks;
        private final Label nameEn = new Label();
        private final Label nameFr = new Label();
        private final Set<String> images = new LinkedHashSet<>();
        private final Set<BirthStatement> births = new LinkedHashSet<>();
        private final Map<String, DeathStatement> deaths = new LinkedHashMap<>();

        private Builder(String qid, int sitelinks) {
            this.qid = qid;
            this.sitelinks = sitelinks;
        }

        private PersonRecord build() {
            return new PersonRecord(
                    qid, sitelinks, nameEn.text, nameFr.text, images, new ArrayList<>(births),
                    new ArrayList<>(deaths.values()));
        }
    }
}
