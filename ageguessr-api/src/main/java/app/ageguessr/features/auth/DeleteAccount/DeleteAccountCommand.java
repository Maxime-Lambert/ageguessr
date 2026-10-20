package app.ageguessr.features.auth.DeleteAccount;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountCommand(@NotBlank String password) {}
