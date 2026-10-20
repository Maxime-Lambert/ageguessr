package app.ageguessr.features.auth.ResetPassword;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ResetPasswordController {

    private final ResetPasswordHandler handler;

    @PostMapping("/api/auth/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordCommand command) {
        handler.handle(command);
        return ResponseEntity.noContent().build();
    }
}
