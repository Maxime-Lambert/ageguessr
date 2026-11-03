package app.ageguessr.features.auth;

import app.ageguessr.shared.i18n.Language;
import java.util.UUID;

/**
 * Public view of the current user, returned by /me and by every use case that changes the
 * profile. {@code role} only lets the front end show admin pages; the API checks the role
 * from the token on every /api/admin/** request. {@code language} is null until chosen:
 * the front end then saves the language of the page.
 */
public record ProfileResponse(
        UUID id, String email, String username, boolean emailVerified, UserRole role, Language language) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.isEmailVerified(),
                user.getRole(),
                user.getLanguage());
    }
}
