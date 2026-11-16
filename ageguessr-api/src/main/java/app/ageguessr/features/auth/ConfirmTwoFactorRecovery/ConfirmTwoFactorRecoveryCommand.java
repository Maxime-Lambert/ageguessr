package app.ageguessr.features.auth.ConfirmTwoFactorRecovery;

import jakarta.validation.constraints.NotBlank;

public record ConfirmTwoFactorRecoveryCommand(@NotBlank String token) {}
