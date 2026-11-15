package app.ageguessr.features.auth.DisableTwoFactor;

import jakarta.validation.constraints.NotBlank;

public record DisableTwoFactorCommand(@NotBlank String password, @NotBlank String code) {}
