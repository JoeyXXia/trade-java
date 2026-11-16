package dev.commerceflow.trade.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("database") @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    public record RegisterRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{2,63}") String loginName,
        @NotNull @Size(min = 12, max = 72) String password) {
        @Override public String toString() { return "RegisterRequest[REDACTED]"; }
    }
    public record LoginRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{2,63}") String loginName,
        @NotNull @Size(min = 1, max = 72) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    public record UserView(String id, String loginName, String role) {}

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request.loginName(), request.password());
    }
    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.loginName(), request.password());
    }
}
