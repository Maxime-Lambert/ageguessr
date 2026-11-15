package app.ageguessr.features.auth.RequestTwoFactorRecovery;

import jakarta.validation.constraints.NotBlank;

public record RequestTwoFactorRecoveryCommand(@NotBlank String challenge) {}
