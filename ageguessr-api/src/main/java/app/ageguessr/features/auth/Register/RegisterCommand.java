package app.ageguessr.features.auth.Register;

import app.ageguessr.shared.validation.Password;
import app.ageguessr.shared.validation.Username;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterCommand(
        @NotBlank @Email String email, @Username String username, @Password String password) {}
