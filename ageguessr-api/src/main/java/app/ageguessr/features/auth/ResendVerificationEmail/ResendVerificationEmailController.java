package app.ageguessr.features.auth.ResendVerificationEmail;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ResendVerificationEmailController {

    private final ResendVerificationEmailHandler handler;

    @PostMapping("/api/auth/resend-verification")
    ResponseEntity<Void> resend(@Valid @RequestBody ResendVerificationEmailCommand command) {
        handler.handle(command);
        return ResponseEntity.accepted().build();
    }
}
