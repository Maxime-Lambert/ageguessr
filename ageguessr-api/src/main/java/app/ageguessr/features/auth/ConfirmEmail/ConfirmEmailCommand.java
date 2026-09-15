package app.ageguessr.features.auth.ConfirmEmail;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailCommand(@NotBlank String token) {}
