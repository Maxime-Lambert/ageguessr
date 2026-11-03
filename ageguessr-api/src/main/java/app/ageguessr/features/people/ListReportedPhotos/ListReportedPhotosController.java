package app.ageguessr.features.people.ListReportedPhotos;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: SecurityConfig requires the ADMIN role on /api/admin/**. */
@RestController
@RequiredArgsConstructor
public class ListReportedPhotosController {

    private final ListReportedPhotosHandler handler;

    @GetMapping("/api/admin/photos/reported")
    ReportedPhotosResponse reported() {
        return handler.handle();
    }
}
