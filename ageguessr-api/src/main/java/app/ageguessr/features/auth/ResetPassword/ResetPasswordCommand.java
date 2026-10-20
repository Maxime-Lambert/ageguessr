package app.ageguessr.features.auth.ResetPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordCommand(@NotBlank String token, @NotBlank @Size(min = 8) String newPassword) {}
