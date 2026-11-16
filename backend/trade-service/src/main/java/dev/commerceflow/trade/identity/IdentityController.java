package dev.commerceflow.trade.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("database")
public class IdentityController {
    private final UserRepository users;
    private final TokenService tokens;
    public IdentityController(UserRepository users, TokenService tokens) { this.users = users; this.tokens = tokens; }
    @GetMapping("/api/me")
    public AuthController.UserView me(@AuthenticationPrincipal Jwt jwt) {
        var user = users.findById(Long.parseLong(jwt.getSubject())).filter(UserAccount::enabled)
            .orElseThrow(TokenService::invalid);
        return new AuthController.UserView(Long.toString(user.id()), user.loginName(), user.role());
    }
    @PostMapping("/api/admin/users/{id}/disable") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable long id) { tokens.disable(id); }
}
