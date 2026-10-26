package app.ageguessr.features.people.IngestPeople;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/** Read-only calls to the Wikimedia Commons API (MediaWiki + Structured Data on Commons). */
@Component
class CommonsClient {

    /** MediaWiki caps "titles" and "ids" at 50 per request for regular clients. */
    static final int BATCH_SIZE = 50;

    private static final String GREGORIAN = "http://www.wikidata.org/entity/Q1985727";

    private final WikimediaHttp http;
    private final WikimediaProperties properties;
    private final IngestProperties ingest;

    CommonsClient(WikimediaHttp http, WikimediaProperties properties, IngestProperties ingest) {
        this.http = http;
        this.properties = properties;
        this.ingest = ingest;
    }

    /** Bitmap files whose Structured Data say they depict the person, as "File:..." titles. */
    List<String> searchDepicting(String wikidataId) {
        JsonNode response = http.get(api()
                .queryParam("action", "query")
                .queryParam("list", "search")
                .queryParam("srnamespace", 6)
                .queryParam("srlimit", ingest.depictsSearchLimit())
                .queryParam("srsearch", "haswbstatement:P180=" + wikidataId + " filetype:bitmap")
                .build()
                .encode()
                .toUri());
        List<String> titles = new ArrayList<>();
        for (JsonNode hit : response.path("query").path("search")) {
            titles.add(hit.path("title").asString());
        }
        return titles;
    }

    /**
     * Files keyed by title ("File:..."). Missing or deleted files are left out. POSTed:
     * fifty long file titles overflow a URL (HTTP 414, seen on a real run), and the
     * MediaWiki API reads query parameters from a form body just as well.
     */
    Map<String, CommonsFile> fetchFiles(Collection<String> titles) {
        Map<String, CommonsFile> files = new LinkedHashMap<>();
        URI endpoint = api().build().encode().toUri();
        for (List<String> batch : batches(List.copyOf(titles))) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("action", "query");
            form.add("prop", "imageinfo");
            form.add("iiprop", "url|mime|extmetadata|metadata");
            form.add("iiurlwidth", String.valueOf(ingest.thumbnailWidth()));
            form.add("iiextmetadatafilter", "DateTimeOriginal|LicenseShortName|LicenseUrl|Artist|UsageTerms");
            form.add("titles", String.join("|", batch));
            JsonNode response = http.postForm(endpoint, form);
            for (JsonNode page : response.path("query").path("pages")) {
                parseFile(page).ifPresent(file -> files.put(file.title(), file));
            }
        }
        return files;
    }

    /** Structured Data keyed by page id. A file without any statement maps to {@link MediaInfo#NONE}. */
    Map<Long, MediaInfo> fetchMediaInfo(Collection<Long> pageIds) {
        Map<Long, MediaInfo> infos = new HashMap<>();
        for (List<Long> batch : batches(List.copyOf(pageIds))) {
            List<String> ids = batch.stream().map(id -> "M" + id).toList();
            JsonNode response = http.get(api()
                    .queryParam("action", "wbgetentities")
                    .queryParam("ids", String.join("|", ids))
                    .build()
                    .encode()
                    .toUri());
            for (JsonNode entity : response.path("entities")) {
                String id = entity.path("id").asString("");
                if (id.startsWith("M")) {
                    infos.put(Long.parseLong(id.substring(1)), parseMediaInfo(entity));
                }
            }
        }
        return infos;
    }

    static Optional<CommonsFile> parseFile(JsonNode page) {
        if (page.has("missing") || page.has("invalid") || !page.path("imageinfo").isArray()
                || page.path("imageinfo").isEmpty()) {
            return Optional.empty();
        }
        JsonNode info = page.path("imageinfo").get(0);
        JsonNode ext = info.path("extmetadata");
        return Optional.of(new CommonsFile(
                page.path("title").asString(),
                page.path("pageid").asLong(),
                text(info.path("mime")),
                text(info.path("thumburl")),
                text(info.path("descriptionurl")),
                text(ext.path("DateTimeOriginal").path("value")),
                exifDateTimeOriginal(info.path("metadata")),
                text(ext.path("LicenseShortName").path("value")),
                text(ext.path("LicenseUrl").path("value")),
                text(ext.path("Artist").path("value"))));
    }

    /**
     * The camera date only. The metadata list also carries "DateTime" (last edit by
     * software), which must never be mistaken for it.
     */
    private static String exifDateTimeOriginal(JsonNode metadata) {
        for (JsonNode entry : metadata) {
            if ("DateTimeOriginal".equals(entry.path("name").asString(""))) {
                return text(entry.path("value"));
            }
        }
        return null;
    }

    static MediaInfo parseMediaInfo(JsonNode entity) {
        // MediaWiki serializes an empty statement map as [] rather than {}.
        JsonNode statements = entity.path("statements");
        if (!statements.isObject()) {
            return MediaInfo.NONE;
        }
        List<PhotoDate> inceptions = new ArrayList<>();
        for (JsonNode statement : statements.path("P571")) {
            if ("deprecated".equals(statement.path("rank").asString(""))) {
                continue;
            }
            JsonNode value = statement.path("mainsnak").path("datavalue").path("value");
            if (!GREGORIAN.equals(value.path("calendarmodel").asString(""))) {
                continue;
            }
            PhotoDate.fromWikibase(value.path("time").asString(""), value.path("precision").asInt(-1))
                    .ifPresent(inceptions::add);
        }
        int depicted = 0;
        for (JsonNode statement : statements.path("P180")) {
            if (!"deprecated".equals(statement.path("rank").asString(""))) {
                depicted++;
            }
        }
        return new MediaInfo(List.copyOf(inceptions), depicted);
    }

    private UriComponentsBuilder api() {
        return UriComponentsBuilder.fromUri(properties.commonsApiUrl())
                .queryParam("format", "json")
                .queryParam("formatversion", 2)
                // Ask MediaWiki to refuse the request when its replicas lag: WikimediaHttp
                // then waits and retries, as the API etiquette asks of bots.
                .queryParam("maxlag", 5);
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    private static <T> List<List<T>> batches(List<T> items) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            batches.add(items.subList(i, Math.min(i + BATCH_SIZE, items.size())));
        }
        return batches;
    }
}
