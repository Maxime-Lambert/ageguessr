package app.ageguessr.features.people.ExcludePerson;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class ExcludePersonController {

    private final ExcludePersonHandler handler;

    @PostMapping("/api/admin/people/{personId}/exclusion")
    ExcludePersonResponse exclude(@PathVariable UUID personId) {
        return handler.handle(new ExcludePersonCommand(personId));
    }
}
