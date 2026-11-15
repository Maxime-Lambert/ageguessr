package app.ageguessr.features.auth.Login;

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

@RestController
@RequiredArgsConstructor
public class LoginController {

    private final LoginHandler handler;
    private final RefreshTokenCookieFactory cookieFactory;

    @PostMapping("/api/auth/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginCommand command) {
        LoginOutcome outcome = handler.handle(command);
        if (outcome.twoFactorChallenge() != null) {
            // No cookie, no token: nothing is signed in until the code.
            return ResponseEntity.ok(new TwoFactorChallengeResponse(true, outcome.twoFactorChallenge()));
        }
        TokenPair tokens = outcome.tokens();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.create(tokens.rawRefreshToken()).toString())
                .body(new LoginResponse(tokens.accessToken()));
    }
}
