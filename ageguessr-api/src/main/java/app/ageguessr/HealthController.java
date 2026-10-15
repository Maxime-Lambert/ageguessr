package app.ageguessr;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public liveness check used by the deploy pipeline's smoke test. */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    String health() {
        return "OK";
    }
}
