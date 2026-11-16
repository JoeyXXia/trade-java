package dev.commerceflow.trade.identity;

import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message) {}
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, AuthService.InvalidInput.class})
    ResponseEntity<ErrorBody> invalid(Exception ignored) { return error(400, "INVALID_REQUEST", "Invalid request"); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorBody> credentials(AuthenticationException ignored) { return error(401, "INVALID_CREDENTIALS", "Invalid credentials"); }
    @ExceptionHandler(LoginThrottle.Limited.class)
    ResponseEntity<ErrorBody> limited(LoginThrottle.Limited ignored) {
        return ResponseEntity.status(429).header("Retry-After", "60")
            .body(new ErrorBody("LOGIN_THROTTLED", "Try again later"));
    }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ErrorBody> duplicate(DuplicateKeyException ignored) { return error(409, "LOGIN_NAME_UNAVAILABLE", "Login name unavailable"); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorBody> database(DataAccessException ignored) { return error(503, "DEPENDENCY_UNAVAILABLE", "Service temporarily unavailable"); }
    private static ResponseEntity<ErrorBody> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorBody(code, message));
    }
}
