package dev.commerceflow.trade.identity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("database")
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authentication;
    public AuthService(UserRepository users, PasswordEncoder encoder, AuthenticationManager authentication) {
        this.users = users; this.encoder = encoder; this.authentication = authentication;
    }
    public static String normalize(String name) { return name.toLowerCase(Locale.ROOT); }

    @Transactional
    public AuthController.UserView register(String login, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new InvalidInput();
        String normalized = normalize(login);
        long id = users.insertBuyer(normalized, encoder.encode(password));
        return new AuthController.UserView(Long.toString(id), normalized, "BUYER");
    }

    public AuthController.UserView login(String login, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new BadCredentialsException("Invalid credentials");
        var result = authentication.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(normalize(login), password));
        var principal = (UserPrincipal) result.getPrincipal();
        return new AuthController.UserView(Long.toString(principal.id()), principal.loginName(), principal.role());
    }

    static class InvalidInput extends RuntimeException {}
}
