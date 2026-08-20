package app.nowtask.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RuleViolationException.class)
    ResponseEntity<Map<String, Object>> ruleViolation(RuleViolationException e) {
        return body(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "message", message == null ? status.getReasonPhrase() : message,
                "at", Instant.now().toString()));
    }
}
