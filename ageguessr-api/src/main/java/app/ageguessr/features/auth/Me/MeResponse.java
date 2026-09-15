package app.ageguessr.features.auth.Me;

import java.util.UUID;

public record MeResponse(UUID id, String email, boolean emailVerified) {}
