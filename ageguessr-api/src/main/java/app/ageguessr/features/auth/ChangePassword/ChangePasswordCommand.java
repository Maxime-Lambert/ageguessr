package app.ageguessr.features.auth.ChangePassword;

import app.ageguessr.shared.validation.Password;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordCommand(@NotBlank String currentPassword, @Password String newPassword) {}
