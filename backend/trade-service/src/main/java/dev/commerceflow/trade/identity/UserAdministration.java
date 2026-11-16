package dev.commerceflow.trade.identity;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("database")
public class UserAdministration {
    private final TokenService tokens;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    public UserAdministration(TokenService tokens, UserRepository users, JdbcTemplate jdbc) {
        this.tokens = tokens; this.users = users; this.jdbc = jdbc;
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public void disable(long id) {
        users.findByIdForUpdate(id).orElseThrow(NotFound::new);
        tokens.disable(id);
        var actor = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        jdbc.update("INSERT INTO identity_audit(id,trace_id,actor_user_id,target_user_id,action,outcome) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID().toString(), TraceIds.current(), Long.parseLong(actor.getToken().getSubject()),
            id, "USER_DISABLED", "SUCCESS");
    }
    public static class NotFound extends RuntimeException {}
}
