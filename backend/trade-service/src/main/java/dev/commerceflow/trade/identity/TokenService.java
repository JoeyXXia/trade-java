package dev.commerceflow.trade.identity;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("database")
public class TokenService {
    private final JdbcTemplate jdbc;
    private final JwtEncoder encoder;
    private final UserRepository users;
    public TokenService(JdbcTemplate jdbc, JwtEncoder encoder, UserRepository users) {
        this.jdbc = jdbc; this.encoder = encoder; this.users = users;
    }
    public record Tokens(String accessToken, String tokenType, long expiresIn,
                         String refreshToken, AuthController.UserView user) {
        @Override public String toString() { return "Tokens[REDACTED]"; }
    }

    @Transactional
    public Tokens issue(AuthController.UserView user) {
        return create(Long.parseLong(user.id()), UUID.randomUUID().toString(), Instant.now().plusSeconds(7 * 86400));
    }

    @Transactional(noRollbackFor = Reused.class)
    public Tokens rotate(String raw) {
        lockTokenUser(raw);
        var rows = jdbc.queryForList("SELECT id,family_id,user_id,expires_at,used_at,revoked_at FROM refresh_session WHERE token_hash=? FOR UPDATE", digest(raw));
        if (rows.isEmpty()) throw invalid();
        var row = rows.getFirst();
        String family = (String) row.get("family_id");
        if (row.get("used_at") != null) {
            jdbc.update("UPDATE refresh_session SET revoked_at=? WHERE family_id=? AND revoked_at IS NULL", Timestamp.from(Instant.now()), family);
            throw new Reused(); // Must commit the family revocation despite returning 401.
        }
        long id = ((Number) row.get("user_id")).longValue();
        if (row.get("revoked_at") != null || !((Timestamp) row.get("expires_at")).toInstant().isAfter(Instant.now())) throw invalid();
        jdbc.update("UPDATE refresh_session SET used_at=? WHERE id=?", Timestamp.from(Instant.now()), row.get("id"));
        return create(id, family, ((Timestamp) row.get("expires_at")).toInstant());
    }

    @Transactional
    public void revoke(String raw) {
        // All session operations lock user first, then token/family: same order as disable.
        var tokenUsers = jdbc.queryForList("SELECT user_id FROM refresh_session WHERE token_hash=?", digest(raw));
        if (tokenUsers.isEmpty()) return;
        users.findByIdForUpdate(((Number)tokenUsers.getFirst().get("user_id")).longValue());
        var rows = jdbc.queryForList("SELECT family_id FROM refresh_session WHERE token_hash=? FOR UPDATE", digest(raw));
        if (!rows.isEmpty()) jdbc.update("UPDATE refresh_session SET revoked_at=? WHERE family_id=? AND revoked_at IS NULL",
            Timestamp.from(Instant.now()), rows.getFirst().get("family_id"));
    }

    @Transactional
    public void disable(long userId) {
        jdbc.update("UPDATE app_user SET enabled=FALSE WHERE id=?", userId);
        jdbc.update("UPDATE refresh_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", Timestamp.from(Instant.now()), userId);
    }

    private Tokens create(long userId, String family, Instant expiry) {
        var user = users.findByIdForUpdate(userId).filter(UserAccount::enabled).orElseThrow(TokenService::invalid);
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO refresh_session(id,family_id,user_id,token_hash,expires_at) VALUES (?,?,?,?,?)",
            UUID.randomUUID().toString(), family, userId, digest(raw), Timestamp.from(expiry));
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(TokenConfiguration.ISSUER)
            .audience(List.of(TokenConfiguration.AUDIENCE)).subject(Long.toString(userId))
            .issuedAt(now).expiresAt(now.plusSeconds(300)).id(UUID.randomUUID().toString()).build();
        String access = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Tokens(access,"Bearer",300,raw,
            new AuthController.UserView(Long.toString(userId), user.loginName(), user.role()));
    }
    private void lockTokenUser(String raw) {
        var rows = jdbc.queryForList("SELECT user_id FROM refresh_session WHERE token_hash=?", digest(raw));
        if (rows.isEmpty()) throw invalid();
        users.findByIdForUpdate(((Number) rows.getFirst().get("user_id")).longValue())
            .filter(UserAccount::enabled).orElseThrow(TokenService::invalid);
    }
    static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static BadCredentialsException invalid() { return new BadCredentialsException("Invalid credentials"); }
    public static class Reused extends BadCredentialsException { public Reused() { super("Invalid credentials"); } }
}
