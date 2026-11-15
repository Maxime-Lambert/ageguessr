package app.ageguessr.features.auth.LoginTotp;

import app.ageguessr.features.auth.LoginResponse;
import app.ageguessr.features.auth.RefreshTokenCookieFactory;
import app.ageguessr.features.auth.TokenPair;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public: the challenge stands for the password just proven. Rate limited under /api/auth/. */
@RestController
@RequiredArgsConstructor
public class LoginTotpController {

    private final LoginTotpHandler handler;
    private final RefreshTokenCookieFactory cookieFactory;

    @PostMapping("/api/auth/login/totp")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginTotpCommand command) {
        TokenPair tokens = handler.handle(command);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.rawRefreshToken()).toString())
                .body(new LoginResponse(tokens.accessToken()));
    }
}
