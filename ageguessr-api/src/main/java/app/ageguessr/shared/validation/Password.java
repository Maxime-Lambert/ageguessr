package app.ageguessr.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The format of a new password: at least 12 characters, and at most 72 bytes once
 * UTF-8 encoded. No composition rule (see docs/decisions/architecture.md). Whether the
 * password is too common is checked by features/auth/PasswordPolicy, which needs the
 * account.
 *
 * The upper bound is bcrypt's, counted in bytes, not characters ("é" is 2 bytes, an
 * emoji 4): BCryptPasswordEncoder throws on anything longer, which would surface as a
 * 500 instead of a validation error. Only for passwords being set - login must accept
 * whatever is typed and simply fail to match.
 */
@NotBlank
@Size(min = 12)
@MaxUtf8Bytes(72)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Password {
    String message() default "must be at least 12 characters and at most 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
