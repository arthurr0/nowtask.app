package app.nowtask.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import app.nowtask.shared.BadRequestException;
import app.nowtask.shared.ConflictException;
import app.nowtask.shared.ForbiddenException;
import app.nowtask.shared.NoOrganizationException;
import app.nowtask.shared.NotFoundException;
import app.nowtask.shared.RuleViolationException;
import app.nowtask.shared.TooManyRequestsException;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        return coded(HttpStatus.CONFLICT, e.getMessage(), e.errorCode(), null);
    }

    @ExceptionHandler(RuleViolationException.class)
    ResponseEntity<Map<String, Object>> ruleViolation(RuleViolationException e) {
        return coded(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage(), e.errorCode(), null);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Map<String, Object>> badRequest(BadRequestException e) {
        return coded(HttpStatus.BAD_REQUEST, e.getMessage(), e.errorCode(), null);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<Map<String, Object>> tooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.retryAfterSeconds()))
                .body(codedBody(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), e.errorCode(), null));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Map<String, Object>> forbidden(ForbiddenException e) {
        return coded(HttpStatus.FORBIDDEN, e.getMessage(), e.errorCode(), e.getDetail());
    }

    @ExceptionHandler(NoOrganizationException.class)
    ResponseEntity<Map<String, Object>> noOrganization(NoOrganizationException e) {
        return coded(HttpStatus.CONFLICT, e.getMessage(), e.errorCode(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> coded(
            HttpStatus status, String message, String code, String detail) {
        return ResponseEntity.status(status).body(codedBody(status, message, code, detail));
    }

    private Map<String, Object> codedBody(HttpStatus status, String message, String code, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message == null ? status.getReasonPhrase() : message);
        body.put("at", Instant.now().toString());
        if (code != null) {
            body.put("code", code);
        }
        if (detail != null) {
            body.put("detail", detail);
        }
        return body;
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "message", message == null ? status.getReasonPhrase() : message,
                "at", Instant.now().toString()));
    }
}
