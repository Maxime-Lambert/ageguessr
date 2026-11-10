package app.ageguessr.features.friends.ListFriends;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ListFriendsController {

    private final ListFriendsHandler handler;

    @GetMapping("/api/friends")
    FriendsResponse list(@AuthenticationPrincipal Jwt jwt) {
        return handler.handle(UUID.fromString(jwt.getSubject()));
    }
}
