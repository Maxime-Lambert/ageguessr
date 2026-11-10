package app.ageguessr.features.friends.SendFriendRequest;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SendFriendRequestController {

    private final SendFriendRequestHandler handler;

    @PostMapping("/api/friends/requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void send(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SendFriendRequestCommand command) {
        handler.handle(UUID.fromString(jwt.getSubject()), command);
    }
}
