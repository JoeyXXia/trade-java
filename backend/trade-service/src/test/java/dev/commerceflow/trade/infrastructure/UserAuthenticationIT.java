package dev.commerceflow.trade.infrastructure;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.system.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("database")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class UserAuthenticationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestImages.locked("MYSQL_IMAGE", "mysql"))
        .withDatabaseName("cf_identity").withUsername("identity_test").withPassword("container-only-test-password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        if (!MYSQL.isRunning()) MYSQL.start();
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    ResponseEntity<String> post(String path, Object body) {
        return http.postForEntity("/api/auth/" + path, body, String.class);
    }
    @Test void hashesPasswordsDefaultsToBuyerAndNormalizesLogin(CapturedOutput output) {
        String raw = "fixture-only-password-123";
        var registered = post("register", Map.of("loginName", "CaseBuyer", "password", raw));
        assertThat(registered.getStatusCode().value()).isEqualTo(201);
        assertThat(registered.getBody()).contains("\"loginName\":\"casebuyer\"", "\"role\":\"BUYER\"").doesNotContain(raw, "passwordHash");
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE login_name='casebuyer'", String.class);
        assertThat(hash).startsWith("$2").hasSize(60).isNotEqualTo(raw);
        assertThat(encoder.matches(raw, hash)).isTrue();
        assertThat(post("login", Map.of("loginName", "CASEBUYER", "password", raw)).getStatusCode().value()).isEqualTo(200);
        assertThat(output.getAll()).doesNotContain(raw, hash);
    }
    @Test void duplicateNamesAreDatabaseConflicts() {
        assertThat(post("register", Map.of("loginName","dup-buyer","password","fixture-password-123")).getStatusCode().value()).isEqualTo(201);
        assertThat(post("register", Map.of("loginName","DUP-BUYER","password","fixture-password-456")).getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE login_name='dup-buyer'", Integer.class)).isEqualTo(1);
    }
    @Test void wrongPasswordAndUnknownUserHaveSamePublicError() {
        post("register",Map.of("loginName","error-buyer","password","fixture-password-123"));
        var wrong = post("login",Map.of("loginName","error-buyer","password","wrong-password-123"));
        var unknown = post("login",Map.of("loginName","unknown-buyer","password","wrong-password-123"));
        assertThat(wrong.getStatusCode().value()).isEqualTo(401);
        assertThat(unknown.getStatusCode()).isEqualTo(wrong.getStatusCode());
        assertThat(unknown.getBody()).isEqualTo(wrong.getBody()).doesNotContain("error-buyer", "unknown-buyer", "password_hash");
    }
    @Test void rejectsInvalidInputUnknownRoleAndBcryptByteOverflow() {
        assertThat(post("register",Map.of("loginName","x","password","short")).getStatusCode().value()).isEqualTo(400);
        assertThat(post("register",Map.of("loginName","role-inject","password","fixture-password-123","role","ADMIN")).getStatusCode().value()).isEqualTo(400);
        assertThat(post("register",Map.of("loginName","byte-overflow","password","密".repeat(30))).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE login_name IN ('role-inject','byte-overflow')", Integer.class)).isZero();
    }
    @Test void anonymousIsRejectedAndNoSessionCookieIsCreated() {
        assertThat(http.getForEntity("/api/private",String.class).getStatusCode().value()).isEqualTo(401);
        var login = post("login",Map.of("loginName","unknown-buyer","password","wrong-password-123"));
        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(http.getForEntity("/actuator/health",String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/actuator/env",String.class).getStatusCode().value()).isEqualTo(404);
    }
}
