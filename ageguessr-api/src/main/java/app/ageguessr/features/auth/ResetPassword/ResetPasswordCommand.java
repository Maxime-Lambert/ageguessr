package app.ageguessr.features.auth.ResetPassword;

import app.ageguessr.shared.validation.Password;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordCommand(@NotBlank String token, @Password String newPassword) {}
