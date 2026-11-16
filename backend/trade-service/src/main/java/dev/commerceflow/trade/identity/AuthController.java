package dev.commerceflow.trade.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("database") @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final TokenService tokens;
    public AuthController(AuthService service, TokenService tokens) { this.service = service; this.tokens = tokens; }

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
    public record RefreshRequest(@NotNull @Pattern(regexp="[A-Za-z0-9_-]{43}") String refreshToken) {
        @Override public String toString() { return "RefreshRequest[REDACTED]"; }
    }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest request) {
        return service.register(request.loginName(), request.password());
    }
    @PostMapping("/login")
    public TokenService.Tokens login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
        return tokens.issue(service.login(request.loginName(), request.password(), http.getRemoteAddr()));
    }
    @PostMapping("/refresh")
    public TokenService.Tokens refresh(@Valid @RequestBody RefreshRequest request) { return tokens.rotate(request.refreshToken()); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) { tokens.revoke(request.refreshToken()); }
}
