package app.ageguessr.features.friends.GetFriendsToday;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GetFriendsTodayController {

    private final GetFriendsTodayHandler handler;

    @GetMapping("/api/friends/today")
    FriendsTodayResponse today(@AuthenticationPrincipal Jwt jwt) {
        return handler.handle(UUID.fromString(jwt.getSubject()));
    }
}
