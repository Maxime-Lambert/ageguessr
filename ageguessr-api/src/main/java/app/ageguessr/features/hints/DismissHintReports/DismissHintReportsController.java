package app.ageguessr.features.hints.DismissHintReports;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class DismissHintReportsController {

    private final DismissHintReportsHandler handler;

    @PostMapping("/api/admin/hints/{hintId}/reports/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dismiss(@PathVariable UUID hintId) {
        handler.handle(hintId);
    }
}
