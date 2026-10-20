package app.ageguessr.features.auth.ConfirmAccountDeletion;

import jakarta.validation.constraints.NotBlank;

public record ConfirmAccountDeletionCommand(@NotBlank String token) {}
