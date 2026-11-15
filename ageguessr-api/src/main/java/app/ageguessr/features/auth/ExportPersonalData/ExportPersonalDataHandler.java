package app.ageguessr.features.auth.ExportPersonalData;

import app.ageguessr.shared.personaldata.PersonalDataSection;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything the site keeps on a player, part by part (PersonalDataSection, one per
 * feature, in their @Order). One read-only transaction: the parts are one snapshot.
 */
@Service
@RequiredArgsConstructor
public class ExportPersonalDataHandler {

    private final List<PersonalDataSection> sections;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Map<String, Object> handle(ExportPersonalDataQuery query) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now(clock));
        for (PersonalDataSection section : sections) {
            export.put(section.name(), section.export(query.userId()));
        }
        return export;
    }
}
