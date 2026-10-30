package app.ageguessr.features.hints.ImportHints;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reads every hint file shipped with the application. A malformed file stops the import. */
@Component
@RequiredArgsConstructor
class HintFileReader {

    private static final String FILES = "classpath:hints/*.json";

    private final ObjectMapper objectMapper;

    List<HintFileEntry> readAll() {
        List<HintFileEntry> entries = new ArrayList<>();
        try {
            for (Resource file : new PathMatchingResourcePatternResolver().getResources(FILES)) {
                try (InputStream content = file.getInputStream()) {
                    entries.addAll(objectMapper.readValue(content, new TypeReference<List<HintFileEntry>>() {}));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }
}
