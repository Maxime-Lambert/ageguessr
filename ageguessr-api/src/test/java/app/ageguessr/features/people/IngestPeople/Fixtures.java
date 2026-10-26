package app.ageguessr.features.people.IngestPeople;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
}
