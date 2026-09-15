package app.ageguessr.features.auth.Register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCommand(
        @NotBlank @Email String email, @NotBlank @Size(min = 8) String password) {}
