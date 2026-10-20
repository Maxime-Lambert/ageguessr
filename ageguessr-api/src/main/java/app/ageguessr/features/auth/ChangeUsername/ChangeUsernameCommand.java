package app.ageguessr.features.auth.ChangeUsername;

import app.ageguessr.shared.validation.Username;

public record ChangeUsernameCommand(@Username String username) {}
