package app.ageguessr.features.hints.ListPendingHints;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: SecurityConfig requires the ADMIN role on /api/admin/**. */
@RestController
@RequiredArgsConstructor
public class ListPendingHintsController {

    private final ListPendingHintsHandler handler;

    @GetMapping("/api/admin/hints/pending")
    PendingHintsResponse pending(@Valid @ModelAttribute ListPendingHintsQuery query) {
        return handler.handle(query);
    }
}
