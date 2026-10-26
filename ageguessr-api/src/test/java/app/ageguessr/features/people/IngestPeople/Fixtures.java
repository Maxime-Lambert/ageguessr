package app.ageguessr.features.people.IngestPeople;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Real Wikimedia responses recorded during the spike (src/test/resources/people/README.md). */
final class Fixtures {

    static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private Fixtures() {}

    static String text(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/people/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("no fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode json(String name) {
        return MAPPER.readTree(text(name));
    }

    /** A WikimediaHttp on a test RestClient (usually bound to MockRestServiceServer), without real waits. */
    static WikimediaHttp http(RestClient client, Sleeper sleeper, Duration pause, int maxAttempts) {
        return new WikimediaHttp(client, MAPPER, sleeper, wikimediaProperties(pause.toMillis(), maxAttempts));
    }

    static WikimediaProperties wikimediaProperties(long pauseMs, int maxAttempts) {
        return new WikimediaProperties(
                "test-agent",
                URI.create("https://qlever.example/api/wikidata"),
                URI.create("https://commons.example/w/api.php"),
                pauseMs,
                maxAttempts,
                1,
                1);
    }

    static IngestProperties ingestProperties(long maxImageBytes) {
        return new IngestProperties(3000, 500, 5, 10, 50, 800, maxImageBytes, 1930);
    }
}
