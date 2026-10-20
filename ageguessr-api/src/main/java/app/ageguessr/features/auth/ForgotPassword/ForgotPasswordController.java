package app.ageguessr.features.auth.ForgotPassword;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final ForgotPasswordHandler handler;

    @PostMapping("/api/auth/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordCommand command) {
        handler.handle(command);
        return ResponseEntity.noContent().build();
    }
}
