package app.ageguessr.features.auth.Logout;

import app.ageguessr.features.auth.RefreshTokenCookieFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LogoutController {

    private final LogoutHandler handler;
    private final RefreshTokenCookieFactory cookieFactory;

    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(
            @CookieValue(value = "${app.refresh-token.cookie-name}", required = false) String rawToken) {
        handler.handle(new LogoutCommand(rawToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }
}
