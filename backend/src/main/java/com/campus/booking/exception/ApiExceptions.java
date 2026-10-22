package com.campus.booking.exception;

import com.campus.booking.booking.BookingConflictException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
    }

    /** One error shape for the whole API: {"status": 409, "message": "...", "fields": {...}}. */
    public record ErrorBody(int status, String message, Map<String, String> fields) {
    }

    @RestControllerAdvice
    public static class Handler {

        @ExceptionHandler(ConflictException.class)
        ResponseEntity<ErrorBody> conflict(ConflictException e) {
            return build(HttpStatus.CONFLICT, e.getMessage(), Map.of());
        }

        @ExceptionHandler(UnauthorizedException.class)
        ResponseEntity<ErrorBody> unauthorized(UnauthorizedException e) {
            return build(HttpStatus.UNAUTHORIZED, e.getMessage(), Map.of());
        }

        @ExceptionHandler(NotFoundException.class)
        ResponseEntity<ErrorBody> notFound(NotFoundException e) {
            return build(HttpStatus.NOT_FOUND, e.getMessage(), Map.of());
        }

        @ExceptionHandler(BadRequestException.class)
        ResponseEntity<ErrorBody> badRequest(BadRequestException e) {
            return build(HttpStatus.BAD_REQUEST, e.getMessage(), Map.of());
        }

        @ExceptionHandler(ForbiddenException.class)
        ResponseEntity<ErrorBody> forbidden(ForbiddenException e) {
            return build(HttpStatus.FORBIDDEN, e.getMessage(), Map.of());
        }

        /** One entry per broken rule, keyed by rule code, so the UI can show exactly what to change. */
        @ExceptionHandler(BookingConflictException.class)
        ResponseEntity<ErrorBody> bookingConflict(BookingConflictException e) {
            Map<String, String> fields = e.getViolations().stream()
                    .collect(Collectors.toMap(v -> v.code().name(), v -> v.message(), (a, b) -> a));
            return build(HttpStatus.CONFLICT, e.getMessage(), fields);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
            Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
                    .collect(Collectors.toMap(FieldError::getField,
                            f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                            (a, b) -> a));
            return build(HttpStatus.BAD_REQUEST, "Validation failed", fields);
        }

        private ResponseEntity<ErrorBody> build(HttpStatus status, String message, Map<String, String> fields) {
            return ResponseEntity.status(status).body(new ErrorBody(status.value(), message, fields));
        }
    }
}
