package app.ageguessr.features.auth;

import java.util.UUID;

/**
 * Public view of the current user, returned by /me and by every use case that changes the
 * profile. {@code role} only lets the front end show admin pages; the API checks the role
 * from the token on every /api/admin/** request.
 */
public record ProfileResponse(UUID id, String email, String username, boolean emailVerified, UserRole role) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(), user.getEmail(), user.getUsername(), user.isEmailVerified(), user.getRole());
    }
}
