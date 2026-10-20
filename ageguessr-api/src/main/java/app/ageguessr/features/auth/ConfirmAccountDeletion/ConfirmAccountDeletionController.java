package app.ageguessr.features.auth.ConfirmAccountDeletion;

import app.ageguessr.features.auth.RefreshTokenCookieFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public: the link may be opened on a device where the user isn't logged in. */
@RestController
@RequiredArgsConstructor
public class ConfirmAccountDeletionController {

    private final ConfirmAccountDeletionHandler handler;
    private final RefreshTokenCookieFactory cookieFactory;

    @PostMapping("/api/auth/confirm-account-deletion")
    ResponseEntity<Void> confirmDeletion(@Valid @RequestBody ConfirmAccountDeletionCommand command) {
        handler.handle(command);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }
}
