package app.ageguessr.features.people.IngestPeople;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;

/**
 * Lists people from Wikidata, most notable first, through QLever: the public Wikidata
 * SPARQL service times out on this query (plans/active-plan.md, Phase 1).
 */
@Component
class WikidataClient {

    private static final String ENTITY_PREFIX = "http://www.wikidata.org/entity/";
    private static final String FILE_PATH_MARKER = "Special:FilePath/";
    private static final String RANK_PREFIX = "http://wikiba.se/ontology#";

    // Labels: every "en*" / "fr*" variant is fetched and the exact language is preferred
    // in Java. On QLever, FILTER(LANG(?l) = "en") misses some English labels (Cristiano
    // Ronaldo, seen on a real run) that STRSTARTS finds.
    // Pages over people, not rows: the sub-query picks one page of people, then each
    // person comes back as several rows (one per image x birth statement x death
    // statement). Sorting by sitelinks puts the best-known people first; the QID breaks
    // ties so pages never overlap.
    private static final String SELECTION = """
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX p: <http://www.wikidata.org/prop/>
            PREFIX psv: <http://www.wikidata.org/prop/statement/value/>
            PREFIX wikibase: <http://wikiba.se/ontology#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?person ?sitelinks ?image ?birth ?birthPrecision ?birthCalendar ?birthRank
                   ?deathStatement ?deathRank ?death ?deathPrecision
                   ?labelEn ?labelEnLang ?labelFr ?labelFrLang WHERE {
              %s
              ?person wdt:P18 ?image .
              ?person p:P569 ?birthStatement .
              ?birthStatement wikibase:rank ?birthRank ;
                              psv:P569 ?birthValue .
              ?birthValue wikibase:timeValue ?birth ;
                          wikibase:timePrecision ?birthPrecision ;
                          wikibase:timeCalendarModel ?birthCalendar .
              OPTIONAL {
                ?person p:P570 ?deathStatement .
                ?deathStatement wikibase:rank ?deathRank .
                OPTIONAL {
                  ?deathStatement psv:P570 ?deathValue .
                  ?deathValue wikibase:timeValue ?death ;
                              wikibase:timePrecision ?deathPrecision .
                }
              }
              OPTIONAL {
                ?person rdfs:label ?labelEn FILTER(STRSTARTS(LANG(?labelEn), "en"))
                BIND(LANG(?labelEn) AS ?labelEnLang)
              }
              OPTIONAL {
                ?person rdfs:label ?labelFr FILTER(STRSTARTS(LANG(?labelFr), "fr"))
                BIND(LANG(?labelFr) AS ?labelFrLang)
              }
            }
            """;

    private final WikimediaHttp http;
    private final WikimediaProperties properties;

    WikidataClient(WikimediaHttp http, WikimediaProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    private static final String PAGE_OF_PEOPLE = """
            {
                SELECT DISTINCT ?person ?sitelinks WHERE {
                  ?person wdt:P31 wd:Q5 ;
                          wdt:P18 ?anyImage ;
                          wikibase:sitelinks ?sitelinks .
                }
                ORDER BY DESC(?sitelinks) ?person
                LIMIT %d OFFSET %d
              }""";

    private static final String ONE_PERSON = """
            VALUES ?person { wd:%s }
              ?person wdt:P31 wd:Q5 ;
                      wikibase:sitelinks ?sitelinks .""";

    private static final Pattern QID = Pattern.compile("^Q[1-9][0-9]{0,11}$");

    /** People ranked {@code offset} to {@code offset + limit - 1} by notoriety. Fewer (or none) past the end. */
    List<PersonRecord> fetchPeople(int offset, int limit) {
        return query(SELECTION.formatted(PAGE_OF_PEOPLE.formatted(limit, offset)));
    }

    /** One person by QID, to (re)ingest them alone. Empty if they are not a human with a P18 image. */
    Optional<PersonRecord> fetchPerson(String wikidataId) {
        // Checked before it reaches the query text: the QID comes from the command line.
        if (!QID.matcher(wikidataId).matches()) {
            throw new IllegalArgumentException("not a Wikidata QID: " + wikidataId);
        }
        return query(SELECTION.formatted(ONE_PERSON.formatted(wikidataId))).stream().findFirst();
    }

    private List<PersonRecord> query(String sparql) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", sparql);
        JsonNode response = http.postForm(properties.qleverUrl(), form);
        return group(response.path("results").path("bindings"));
    }

    static List<PersonRecord> group(JsonNode bindings) {
        Map<String, Builder> people = new LinkedHashMap<>();
        for (JsonNode row : bindings) {
            String qid = stripPrefix(value(row, "person"), ENTITY_PREFIX);
            Builder person = people.computeIfAbsent(qid, id -> new Builder(id, Integer.parseInt(value(row, "sitelinks"))));
            person.nameEn.offer(value(row, "labelEn"), value(row, "labelEnLang"), "en");
            person.nameFr.offer(value(row, "labelFr"), value(row, "labelFrLang"), "fr");
            String image = value(row, "image");
            if (image != null && image.contains(FILE_PATH_MARKER)) {
                String encoded = image.substring(image.indexOf(FILE_PATH_MARKER) + FILE_PATH_MARKER.length());
                person.images.add(URLDecoder.decode(encoded, StandardCharsets.UTF_8));
            }
            person.births.add(new BirthStatement(
                    value(row, "birth"),
                    Integer.parseInt(value(row, "birthPrecision")),
                    value(row, "birthCalendar"),
                    rank(value(row, "birthRank"))));
            String deathStatement = value(row, "deathStatement");
            if (deathStatement != null) {
                String precision = value(row, "deathPrecision");
                person.deaths.put(deathStatement, new DeathStatement(
                        value(row, "death"), precision == null ? null : Integer.valueOf(precision), rank(value(row, "deathRank"))));
            }
        }
        return people.values().stream().map(Builder::build).toList();
    }

    private static String value(JsonNode row, String variable) {
        JsonNode node = row.path(variable).path("value");
        return node.isMissingNode() ? null : node.asString();
    }

    private static String stripPrefix(String uri, String prefix) {
        return uri.startsWith(prefix) ? uri.substring(prefix.length()) : uri;
    }

    private static BirthStatement.Rank rank(String uri) {
        return switch (stripPrefix(uri, RANK_PREFIX)) {
            case "PreferredRank" -> BirthStatement.Rank.PREFERRED;
            case "DeprecatedRank" -> BirthStatement.Rank.DEPRECATED;
            default -> BirthStatement.Rank.NORMAL;
        };
    }

    /** Keeps the label of the exact language if there is one, else the first regional variant (en-gb...). */
    private static final class Label {
        private String text;
        private boolean exact;

        void offer(String candidate, String lang, String wanted) {
            if (candidate == null || exact) {
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
        // The same statement repeats on every row of the cartesian product: keep it once.
        private final Set<BirthStatement> births = new LinkedHashSet<>();
        private final Map<String, DeathStatement> deaths = new LinkedHashMap<>();

        private Builder(String qid, int sitelinks) {
            this.qid = qid;
            this.sitelinks = sitelinks;
        }

        private PersonRecord build() {
            return new PersonRecord(
                    qid, sitelinks, nameEn.text, nameFr.text, images, new ArrayList<>(births), new ArrayList<>(deaths.values()));
        }
    }
}
