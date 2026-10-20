package app.ageguessr.shared.exceptions;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private final String errorCode;

    public ValidationException(String message) {
        this("VALIDATION_ERROR", message);
    }

    public ValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
