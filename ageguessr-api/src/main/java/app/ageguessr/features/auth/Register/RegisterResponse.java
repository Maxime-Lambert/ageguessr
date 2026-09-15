package app.ageguessr.features.auth.Register;

import java.util.UUID;

public record RegisterResponse(UUID userId, String email) {}
