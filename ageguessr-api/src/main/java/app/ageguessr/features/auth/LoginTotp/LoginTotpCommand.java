package app.ageguessr.features.auth.LoginTotp;

import jakarta.validation.constraints.NotBlank;

/** @param challenge from POST /api/auth/login, @param code from the authenticator app */
public record LoginTotpCommand(@NotBlank String challenge, @NotBlank String code) {}
