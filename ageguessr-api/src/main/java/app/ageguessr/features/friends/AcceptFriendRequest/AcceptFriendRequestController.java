package app.ageguessr.features.friends.AcceptFriendRequest;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AcceptFriendRequestController {

    private final AcceptFriendRequestHandler handler;

    @PostMapping("/api/friends/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        handler.handle(UUID.fromString(jwt.getSubject()), id);
    }
}
