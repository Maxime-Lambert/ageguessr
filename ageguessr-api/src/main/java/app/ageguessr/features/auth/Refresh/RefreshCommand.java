package app.ageguessr.features.auth.Refresh;

import jakarta.validation.constraints.NotBlank;

/** Constructed by the controller from the refresh-token cookie, never a @RequestBody. */
public record RefreshCommand(@NotBlank String rawToken) {}
