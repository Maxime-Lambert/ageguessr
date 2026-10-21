package app.ageguessr.features.auth.Register;

import app.ageguessr.shared.validation.Password;
import app.ageguessr.shared.validation.Username;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code acceptTerms} is a primitive: a request without the field reads as false and fails
 * {@code @AssertTrue}, whereas a null {@code Boolean} would pass it.
 */
public record RegisterCommand(
        @NotBlank @Email String email,
        @Username String username,
        @Password String password,
        @AssertTrue boolean acceptTerms) {}
