package app.ageguessr.features.auth.SetUpTwoFactor;

import jakarta.validation.constraints.NotBlank;

public record SetUpTwoFactorCommand(@NotBlank String password) {}
