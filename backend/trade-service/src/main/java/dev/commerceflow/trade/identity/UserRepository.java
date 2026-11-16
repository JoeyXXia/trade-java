package dev.commerceflow.trade.identity;

import java.sql.Statement;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<UserAccount> findByLogin(String name) {
        return jdbc.query("SELECT id, login_name, password_hash, role, enabled FROM app_user WHERE login_name = ?",
            (rs, row) -> new UserAccount(rs.getLong("id"), rs.getString("login_name"),
                rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("enabled")), name).stream().findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        return jdbc.query("SELECT id, login_name, password_hash, role, enabled FROM app_user WHERE id = ?",
            (rs, row) -> new UserAccount(rs.getLong("id"), rs.getString("login_name"),
                rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("enabled")), id).stream().findFirst();
    }
    public Optional<UserAccount> findByIdForUpdate(long id) {
        return jdbc.query("SELECT id, login_name, password_hash, role, enabled FROM app_user WHERE id = ? FOR UPDATE",
            (rs, row) -> new UserAccount(rs.getLong("id"), rs.getString("login_name"),
                rs.getString("password_hash"), rs.getString("role"), rs.getBoolean("enabled")), id).stream().findFirst();
    }

    public long insertBuyer(String login, String hash) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO app_user(login_name, password_hash, role) VALUES (?, ?, 'BUYER')",
                Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, login);
            statement.setString(2, hash);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
