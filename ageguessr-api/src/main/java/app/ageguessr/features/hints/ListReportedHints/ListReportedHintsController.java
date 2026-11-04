package app.ageguessr.features.hints.ListReportedHints;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: SecurityConfig requires the ADMIN role on /api/admin/**. */
@RestController
@RequiredArgsConstructor
public class ListReportedHintsController {

    private final ListReportedHintsHandler handler;

    @GetMapping("/api/admin/hints/reported")
    ReportedHintsResponse reported() {
        return handler.handle();
    }
}
