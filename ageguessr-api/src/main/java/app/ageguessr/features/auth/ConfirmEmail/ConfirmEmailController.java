package app.ageguessr.features.auth.ConfirmEmail;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConfirmEmailController {

    private final ConfirmEmailHandler handler;

    @PostMapping("/api/auth/confirm-email")
    ResponseEntity<Void> confirmEmail(@Valid @RequestBody ConfirmEmailCommand command) {
        handler.handle(command);
        return ResponseEntity.noContent().build();
    }
}
