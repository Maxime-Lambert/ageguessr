package app.ageguessr.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed constraint: a single place for the username format, shared by registration
 * and username change. 3-20 chars of letters, digits, "_" or "-" - no spaces (and no
 * silent trim), no "@" so a username can never be mistaken for an email. Mirrored by
 * the frontend for instant feedback, but this is the source of truth.
 */
@NotNull
@Pattern(regexp = "^[A-Za-z0-9_-]{3,20}$")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Username {
    String message() default "must be 3-20 characters: letters, digits, '_' or '-'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
