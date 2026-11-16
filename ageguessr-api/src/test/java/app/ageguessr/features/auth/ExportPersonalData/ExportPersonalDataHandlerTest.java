package app.ageguessr.features.auth.ExportPersonalData;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.shared.personaldata.PersonalDataSection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportPersonalDataHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    /** A section that answers with the id it was asked for, to check who it is asked for. */
    private record Section(String name) implements PersonalDataSection {
        @Override
        public Set<String> tables() {
            return Set.of(name);
        }

        @Override
        public Object export(UUID userId) {
            return name + ":" + userId;
        }
    }

    @Test
    void assemblesEverySectionForThePlayerInTheirOrderAfterTheDate() {
        UUID userId = UUID.randomUUID();
        ExportPersonalDataHandler handler = new ExportPersonalDataHandler(
                List.of(new Section("account"), new Section("games"), new Section("friends")),
                Clock.fixed(NOW, ZoneOffset.UTC));

        Map<String, Object> export = handler.handle(new ExportPersonalDataQuery(userId));

        assertThat(export.keySet()).containsExactly("exportedAt", "account", "games", "friends");
        assertThat(export.get("exportedAt")).isEqualTo(NOW);
        assertThat(export.get("games")).isEqualTo("games:" + userId);
    }
}
