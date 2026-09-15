package app.ageguessr.features.auth.Refresh;

import app.ageguessr.features.auth.RefreshTokenCookieFactory;
import app.ageguessr.features.auth.TokenPair;
import app.ageguessr.shared.exceptions.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefreshController {

    private final RefreshHandler handler;
    private final RefreshTokenCookieFactory cookieFactory;

    @PostMapping("/api/auth/refresh")
    ResponseEntity<RefreshResponse> refresh(
            @CookieValue(value = "${app.refresh-token.cookie-name}", required = false) String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        TokenPair tokens = handler.handle(new RefreshCommand(rawToken));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.rawRefreshToken()).toString())
                .body(new RefreshResponse(tokens.accessToken()));
    }
}
