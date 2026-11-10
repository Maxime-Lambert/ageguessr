package app.ageguessr.features.friends.AcceptInvite;

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
public class AcceptInviteController {

    private final AcceptInviteHandler handler;

    @PostMapping("/api/friends/invite/{code}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        handler.handle(UUID.fromString(jwt.getSubject()), code);
    }
}
