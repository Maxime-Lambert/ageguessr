package app.ageguessr.features.auth.ExportPersonalData;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The player's data as a JSON file to keep (GDPR access and portability). Under
 * /api/auth/, so rate limited with the other account requests.
 */
@RestController
@RequiredArgsConstructor
public class ExportPersonalDataController {

    private final ExportPersonalDataHandler handler;
    private final Clock clock;

    @GetMapping("/api/auth/me/data")
    ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal Jwt jwt) {
        String filename = "ageguessr-data-" + LocalDate.now(clock) + ".json";
        return ResponseEntity.ok()
                // A personal file: no cache, shared or in the browser, keeps it.
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(handler.handle(new ExportPersonalDataQuery(UUID.fromString(jwt.getSubject()))));
    }
}
