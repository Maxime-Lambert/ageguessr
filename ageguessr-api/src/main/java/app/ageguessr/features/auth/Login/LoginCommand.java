package app.ageguessr.features.auth.Login;

import jakarta.validation.constraints.NotBlank;

public record LoginCommand(@NotBlank String email, @NotBlank String password) {}
