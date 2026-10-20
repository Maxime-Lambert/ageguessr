package app.ageguessr.features.auth;

import java.util.UUID;

/** Public view of the current user, returned by /me and by every use case that changes the profile. */
public record ProfileResponse(UUID id, String email, String username, boolean emailVerified) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getUsername(), user.isEmailVerified());
    }
}
