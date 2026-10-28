package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.WikidataId;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
 * takes ~7 s: ranking ~4 s, then five queries restricted to the page's QIDs (VALUES) of
 * about a second each or less.
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

    // The QID breaks sitelinks ties, so that pages never overlap. DISTINCT: a person with
    // several birth dates would otherwise come once per date.
    private static final String PAGE = """
            SELECT DISTINCT ?person ?sitelinks WHERE {
              ?person wdt:P31 wd:Q5 ; wdt:P18 ?image ; wikibase:sitelinks ?sitelinks ;
                      wdt:P569 ?birth .
              FILTER(YEAR(?birth) >= %d)
              %s
            }
            ORDER BY DESC(?sitelinks) ?person
            LIMIT %d OFFSET %d
            """;

    // The ranking of one balance cell: people with a P21 and a nationality on the cell's
    // continent. Someone with several nationalities comes up in several cells; the
    // handler keeps them only in the cell ContinentResolver gives them. ~1 to 3.5 s on
    // QLever, deep offsets included.
    private static final String CELL_PAGE = """
            SELECT DISTINCT ?person ?sitelinks WHERE {
              VALUES ?continent { %s }
              ?person wdt:P31 wd:Q5 ; wdt:P18 ?image ; wikibase:sitelinks ?sitelinks ;
                      wdt:P569 ?birth ; wdt:P21 ?gender ; wdt:P27 ?country .
              ?country wdt:P30 ?continent .
              %s
              FILTER(YEAR(?birth) >= %d)
              %s
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

    // Every "en*" / "fr*" label with its language, plus "mul": the best one is chosen in
    // Java. On QLever, FILTER(LANG(?l) = "en") misses some English labels (Cristiano
    // Ronaldo, seen on a real run) that STRSTARTS finds. "mul" (multiple languages):
    // since 2024 Wikidata replaces labels identical across languages with one "mul"
    // label, so Douglas Adams or Shakira have no "en" label any more (14 of the top 200
    // were skipped for want of a name, seen on 2026-10-27).
    private static final String LABELS = """
            SELECT ?person ?label ?lang WHERE {
              VALUES ?person { %s }
              ?person rdfs:label ?label .
              BIND(LANG(?label) AS ?lang)
              FILTER(STRSTARTS(?lang, "en") || STRSTARTS(?lang, "fr") || ?lang = "mul")
            }
            """;

    // Gender and continents of nationality, for the pool balance, and occupations, for
    // the report. wdt: keeps only the best-ranked statements. One UNION rather than
    // separate queries: ~0.5 s for 500 people.
    private static final String PROFILES = """
            SELECT ?person ?gender ?continent ?occupation WHERE {
              VALUES ?person { %s }
              { ?person wdt:P21 ?gender . }
              UNION
              { ?person wdt:P27 ?country . ?country wdt:P30 ?continent . }
              UNION
              { ?person wdt:P106 ?occupation . }
            }
            """;

    private final WikimediaHttp http;
    private final WikimediaProperties properties;

    WikidataClient(WikimediaHttp http, WikimediaProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    /**
     * The people {@code filter} lets through, ranked {@code offset} to
     * {@code offset + limit - 1} by notoriety. Fewer (or none) past the end.
     */
    List<PersonRecord> fetchPeople(int offset, int limit, RankingFilter filter) {
        return details(query(PAGE.formatted(filter.minBirthYear(), filter.clauses(), limit, offset)));
    }

    /**
     * The people of one balance cell, ranked {@code offset} to {@code offset + limit - 1}
     * by notoriety. Includes people who belong to another cell (see CELL_PAGE): the
     * caller filters with {@link Cell#of}.
     */
    List<PersonRecord> fetchCell(Cell cell, int offset, int limit, RankingFilter filter) {
        String continents = entities(ContinentResolver.qidsOf(cell.continent()));
        String gender = cell.gender() == Gender.OTHER
                ? "FILTER(?gender NOT IN (" + String.join(", ", prefixed(GenderResolver.knownQids())) + "))"
                : "VALUES ?gender { " + entities(GenderResolver.qidsOf(cell.gender())) + " }";
        return details(query(CELL_PAGE.formatted(
                continents, gender, filter.minBirthYear(), filter.clauses(), limit, offset)));
    }

    /**
     * Every human the editorial line keeps out, with the criteria that catch them: one
     * query per criterion over all of Wikidata (~45 000 QIDs, under 4 s in all on
     * 2026-10-28).
     */
    Map<String, Set<EditorialLine.Criterion>> fetchEditorialExclusions() {
        Map<String, Set<EditorialLine.Criterion>> caught = new HashMap<>();
        for (EditorialLine.Criterion criterion : EditorialLine.Criterion.values()) {
            for (JsonNode row : query(EditorialLine.query(criterion))) {
                String qid = qid(row);
                if (qid != null) {
                    caught.computeIfAbsent(qid, q -> EnumSet.noneOf(EditorialLine.Criterion.class)).add(criterion);
                }
            }
        }
        return caught;
    }

    /** Sorted, so that a query is always the same text. QIDs from our own constants only. */
    private static List<String> prefixed(Set<String> qids) {
        return qids.stream().sorted().map(q -> "wd:" + q).toList();
    }

    private static String entities(Set<String> qids) {
        return String.join(" ", prefixed(qids));
    }

    /**
     * One person by QID, to (re)ingest them alone. Empty if they are not a human with a P18
     * image. No birth year filter: naming someone is a deliberate choice.
     */
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
                query(LABELS.formatted(values)),
                query(PROFILES.formatted(values)));
    }

    /** Builds the people of a page from the six result sets, in ranking order. */
    static List<PersonRecord> assemble(
            JsonNode ranking, JsonNode births, JsonNode deaths, JsonNode images, JsonNode labels, JsonNode profiles) {
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
        for (JsonNode row : profiles) {
            Builder person = people.get(qid(row));
            if (person != null) {
                entityId(value(row, "gender")).ifPresent(person.genders::add);
                entityId(value(row, "continent")).ifPresent(person.continents::add);
                entityId(value(row, "occupation")).ifPresent(person.occupations::add);
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

    /** "Q6581072" from "http://www.wikidata.org/entity/Q6581072"; empty for anything else. */
    private static Optional<String> entityId(String uri) {
        if (uri == null || !uri.startsWith(ENTITY_PREFIX)) {
            return Optional.empty();
        }
        String id = uri.substring(ENTITY_PREFIX.length());
        return WikidataId.isValid(id) ? Optional.of(id) : Optional.empty();
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

    /**
     * Keeps the label of the exact language if there is one, else the first regional
     * variant (en-gb...), else the "mul" label shared by many languages.
     */
    private static final class Label {
        private static final String MULTIPLE_LANGUAGES = "mul";
        private static final int EXACT = 3;
        private static final int VARIANT = 2;
        private static final int MULTIPLE = 1;

        private String text;
        private int quality;

        void offer(String candidate, String lang, String wanted) {
            if (candidate == null || lang == null) {
                return;
            }
            int offered = wanted.equals(lang)
                    ? EXACT
                    : lang.startsWith(wanted) ? VARIANT : MULTIPLE_LANGUAGES.equals(lang) ? MULTIPLE : 0;
            if (offered > quality) {
                text = candidate;
                quality = offered;
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
        private final Set<String> genders = new LinkedHashSet<>();
        private final Set<String> continents = new LinkedHashSet<>();
        private final Set<String> occupations = new LinkedHashSet<>();

        private Builder(String qid, int sitelinks) {
            this.qid = qid;
            this.sitelinks = sitelinks;
        }

        private PersonRecord build() {
            return new PersonRecord(
                    qid, sitelinks, nameEn.text, nameFr.text, images, new ArrayList<>(births),
                    new ArrayList<>(deaths.values()),
                    GenderResolver.resolve(genders).orElse(null),
                    ContinentResolver.resolve(continents).orElse(null),
                    occupations);
        }
    }
}
