package app.ageguessr.features.auth.EnableTwoFactor;

import jakarta.validation.constraints.NotBlank;

public record EnableTwoFactorCommand(@NotBlank String code) {}
