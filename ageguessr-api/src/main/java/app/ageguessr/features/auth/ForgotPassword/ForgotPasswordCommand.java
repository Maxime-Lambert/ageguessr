package app.ageguessr.features.auth.ForgotPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordCommand(@NotBlank @Email String email) {}
