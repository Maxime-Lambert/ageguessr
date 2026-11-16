package app.ageguessr.features.auth.ConfirmTwoFactorRecovery;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public: the link may be opened on a device where the player is not signed in. */
@RestController
@RequiredArgsConstructor
public class ConfirmTwoFactorRecoveryController {

    private final ConfirmTwoFactorRecoveryHandler handler;

    @PostMapping("/api/auth/disable-two-factor")
    ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmTwoFactorRecoveryCommand command) {
        handler.handle(command);
        return ResponseEntity.noContent().build();
    }
}
