package app.ageguessr.features.auth.RequestTwoFactorRecovery;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RequestTwoFactorRecoveryController {

    private final RequestTwoFactorRecoveryHandler handler;

    @PostMapping("/api/auth/login/totp/recovery")
    ResponseEntity<Void> request(@Valid @RequestBody RequestTwoFactorRecoveryCommand command) {
        handler.handle(command);
        return ResponseEntity.accepted().build();
    }
}
