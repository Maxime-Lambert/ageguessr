package app.ageguessr.features.game.GetHistory;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in account's own history: authenticated (SecurityConfig), never another's. */
@RestController
@RequiredArgsConstructor
public class GetHistoryController {

    private final GetHistoryHandler handler;

    @GetMapping("/api/game/history")
    HistoryResponse history(@AuthenticationPrincipal Jwt jwt, @Valid GetHistoryQuery query) {
        return handler.handle(UUID.fromString(jwt.getSubject()), query.pageOrFirst());
    }
}
