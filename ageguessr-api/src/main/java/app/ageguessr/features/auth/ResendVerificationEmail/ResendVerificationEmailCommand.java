package app.ageguessr.features.auth.ResendVerificationEmail;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationEmailCommand(@NotBlank @Email String email) {}
