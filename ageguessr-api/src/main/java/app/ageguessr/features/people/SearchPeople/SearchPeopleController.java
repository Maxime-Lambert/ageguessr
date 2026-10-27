package app.ageguessr.features.people.SearchPeople;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class SearchPeopleController {

    private final SearchPeopleHandler handler;

    @GetMapping("/api/admin/people")
    SearchPeopleResponse search(@Valid @ModelAttribute SearchPeopleQuery query) {
        return handler.handle(query);
    }
}
