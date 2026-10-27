package app.ageguessr.features.people.ListPendingPhotos;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: SecurityConfig requires the ADMIN role on /api/admin/**. */
@RestController
@RequiredArgsConstructor
public class ListPendingPhotosController {

    private final ListPendingPhotosHandler handler;

    @GetMapping("/api/admin/photos/pending")
    PendingPhotosResponse pending(@Valid @ModelAttribute ListPendingPhotosQuery query) {
        return handler.handle(query);
    }
}
