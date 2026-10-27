package app.ageguessr.features.auth;

/** Set by hand in SQL only (see V5__add_user_role.sql): no endpoint grants a role. */
public enum UserRole {
    USER,
    /** Curates the people of the game (/api/admin/**). */
    ADMIN
}
