package app.ageguessr.features.auth.Register;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterHandler handler;

    @PostMapping("/api/auth/register")
    ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(handler.handle(command));
    }
}
