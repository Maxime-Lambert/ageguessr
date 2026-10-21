package app.ageguessr.config;

import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.ErrorResponse;
import app.ageguessr.shared.exceptions.ForbiddenException;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.exceptions.UnauthorizedException;
import app.ageguessr.shared.exceptions.ValidationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    /**
     * Unreadable body: malformed JSON, or a field Jackson cannot map (e.g. a missing
     * primitive). The message stays generic so the parser's details are not echoed back.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MALFORMED_REQUEST", "The request body could not be read"));
    }

    /**
     * Spring MVC's own request errors (wrong method, unsupported content type, unknown
     * route...) implement {@link org.springframework.web.ErrorResponse} and carry their
     * status. Without this, the catch-all below would turn every one of them into a 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            return springMvcError(ex, springError);
        }
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    /**
     * Keeps the status and headers (e.g. {@code Allow}) of the Spring error, with a generic
     * message. A 5xx one (a {@code ResponseStatusException} thrown with a server status) is
     * still a server failure: it gets logged like any unexpected error.
     */
    private ResponseEntity<ErrorResponse> springMvcError(Exception ex, org.springframework.web.ErrorResponse springError) {
        HttpStatusCode status = springError.getStatusCode();
        if (status.is5xxServerError()) {
            log.error("Unexpected error", ex);
        }
        HttpStatus known = HttpStatus.resolve(status.value());
        String code = known != null ? known.name() : "REQUEST_ERROR";
        String message = known != null ? known.getReasonPhrase() : "The request could not be processed";
        return ResponseEntity.status(status).headers(springError.getHeaders()).body(new ErrorResponse(code, message));
    }
}
