package app.ageguessr.features.auth.DeleteAccount;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** POST rather than DELETE-with-a-body: some proxies and HTTP clients drop the body of a DELETE. */
@RestController
@RequiredArgsConstructor
public class DeleteAccountController {

    private final DeleteAccountHandler handler;

    @PostMapping("/api/auth/me/deletion")
    ResponseEntity<Void> requestDeletion(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountCommand command) {
        handler.handle(UUID.fromString(jwt.getSubject()), command);
        // 202: nothing is deleted yet, the emailed link has to be confirmed first.
        return ResponseEntity.accepted().build();
    }
}
