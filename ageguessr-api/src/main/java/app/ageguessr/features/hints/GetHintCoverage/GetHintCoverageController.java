package app.ageguessr.features.hints.GetHintCoverage;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: SecurityConfig requires the ADMIN role on /api/admin/**. */
@RestController
@RequiredArgsConstructor
public class GetHintCoverageController {

    private final GetHintCoverageHandler handler;

    @GetMapping("/api/admin/hints/coverage")
    HintCoverageResponse coverage() {
        return handler.handle();
    }
}
