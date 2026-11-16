package dev.commerceflow.trade.identity;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("database")
public class LoginThrottle {
    private final JdbcTemplate jdbc;
    public LoginThrottle(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void reserve(String login, String source) {
        String bucket = TokenService.digest(login + "\n" + source);
        var now = Instant.now();
        jdbc.update("INSERT INTO login_throttle(bucket_hash, attempts, window_start) VALUES (?,0,?) ON DUPLICATE KEY UPDATE bucket_hash=bucket_hash",
            bucket, Timestamp.from(now));
        var row = jdbc.queryForMap("SELECT attempts, window_start FROM login_throttle WHERE bucket_hash=? FOR UPDATE", bucket);
        boolean reset = ((Timestamp) row.get("window_start")).toInstant().plusSeconds(60).isBefore(now);
        int count = reset ? 0 : ((Number) row.get("attempts")).intValue();
        if (count >= 10) throw new Limited();
        jdbc.update("UPDATE login_throttle SET attempts=?,window_start=? WHERE bucket_hash=?",
            count + 1, reset ? Timestamp.from(now) : row.get("window_start"), bucket);
    }
    public void success(String login, String source) {
        jdbc.update("DELETE FROM login_throttle WHERE bucket_hash=?", TokenService.digest(login + "\n" + source));
    }
    public static class Limited extends RuntimeException {}
}
