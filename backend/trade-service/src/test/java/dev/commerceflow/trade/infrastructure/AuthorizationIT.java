package dev.commerceflow.trade.infrastructure;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import dev.commerceflow.trade.identity.ResourceAccess;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(AuthorizationIT.FixtureEndpoints.class)
@ActiveProfiles({"database","authorization-fixture"}) @Testcontainers
class AuthorizationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>(TestImages.locked("MYSQL_IMAGE","mysql"))
        .withDatabaseName("cf_authorization").withUsername("authz_test").withPassword("container-only-test-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        if(!MYSQL.isRunning()) MYSQL.start();
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
            .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);
        p.add("spring.datasource.username",MYSQL::getUsername);
        p.add("spring.datasource.password",MYSQL::getPassword);
        byte[] key=new byte[64]; new java.security.SecureRandom().nextBytes(key);
        String secret=Base64.getEncoder().encodeToString(key);
        p.add("commerceflow.jwt-secret-base64",()->secret);
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    JsonNode user(String role) throws Exception {
        String name="u"+UUID.randomUUID().toString().replace("-","");
        var body=Map.of("loginName",name,"password","fixture-password-123");
        assertThat(http.postForEntity("/api/auth/register",body,String.class).getStatusCode().value()).isEqualTo(201);
        var response=http.postForEntity("/api/auth/login",body,String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var result=json.readTree(response.getBody());
        jdbc.update("UPDATE app_user SET role=? WHERE id=?",role,id(result)); // Test fixture, no public role mutation.
        return result;
    }
    long id(JsonNode u) {return u.path("user").path("id").asLong();}
    ResponseEntity<String> call(HttpMethod method,String path,JsonNode u) {
        var h=new HttpHeaders();
        h.setBearerAuth(u.path("accessToken").asText());
        h.set("X-User-Id","1");h.set("X-Trace-Id","forged-trace");
        return http.exchange(path,method,new HttpEntity<>(h),String.class);
    }
    @Test void buyerCanReadOnlyItsOwnUserAndForgedIdentityIsIgnored() throws Exception {
        var a=user("BUYER");var b=user("BUYER");
        var me=call(HttpMethod.GET,"/api/me?userId="+id(b),a);
        assertThat(json.readTree(me.getBody()).path("id").asLong()).isEqualTo(id(a));
        assertThat(call(HttpMethod.GET,"/api/users/"+id(a),a).getStatusCode().value()).isEqualTo(200);
        var denied=call(HttpMethod.GET,"/api/users/"+id(b)+"?userId="+id(a),a);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        var error=json.readTree(denied.getBody());
        assertThat(error.path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(error.path("traceId").asText()).isEqualTo(denied.getHeaders().getFirst("X-Trace-Id"));
        assertThat(UUID.fromString(error.path("traceId").asText())).isNotNull();
        assertThat(denied.getBody()).doesNotContain("forged-trace",b.path("user").path("loginName").asText());
    }
    @Test void persistedOrderOwnerControlsAccessNotCallerSuppliedUserId() throws Exception {
        var a=user("BUYER");var b=user("BUYER");
        jdbc.execute("CREATE TABLE IF NOT EXISTS fixture_owned_order(id BIGINT PRIMARY KEY, buyer_id BIGINT NOT NULL)");
        long orderId=id(a);
        jdbc.update("INSERT INTO fixture_owned_order(id,buyer_id) VALUES (?,?)",orderId,id(b));
        assertThat(call(HttpMethod.GET,"/api/test-owned-order/"+orderId+"?userId="+id(a),a).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.GET,"/api/test-owned-order/"+orderId,b).getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT buyer_id FROM fixture_owned_order WHERE id=?",Long.class,orderId)).isEqualTo(id(b));
    }
    @Test void roleMatrixAndServiceMethodSecurityRejectUnauthorizedWrites() throws Exception {
        var buyer=user("BUYER");var operator=user("OPERATOR");var admin=user("ADMIN");
        assertThat(call(HttpMethod.POST,"/api/test-operations",buyer).getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.POST,"/api/test-operations",operator).getStatusCode().value()).isEqualTo(204);
        assertThat(call(HttpMethod.POST,"/api/test-operations",admin).getStatusCode().value()).isEqualTo(204);
        for(var caller:List.of(buyer,operator)) {
            assertThat(call(HttpMethod.POST,"/api/admin/users/"+id(buyer)+"/disable",caller).getStatusCode().value()).isEqualTo(403);
            assertThat(call(HttpMethod.POST,"/api/test-disable/"+id(buyer),caller).getStatusCode().value()).isEqualTo(403);
        }
        assertThat(jdbc.queryForObject("SELECT enabled FROM app_user WHERE id=?",Boolean.class,id(buyer))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_audit WHERE target_user_id=?",Integer.class,id(buyer))).isZero();
        assertThat(http.postForEntity("/api/test-operations",null,String.class).getStatusCode().value()).isEqualTo(401);
    }
    @Test void adminDisableAndAuditCommitTogetherWithTrustedActorAndTrace() throws Exception {
        var buyer=user("BUYER");var admin=user("ADMIN");
        var response=call(HttpMethod.POST,"/api/admin/users/"+id(buyer)+"/disable?actorUserId="+id(buyer),admin);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        var audit=jdbc.queryForMap("SELECT * FROM identity_audit WHERE target_user_id=?",id(buyer));
        assertThat(((Number)audit.get("actor_user_id")).longValue()).isEqualTo(id(admin));
        assertThat(audit.get("trace_id")).isEqualTo(response.getHeaders().getFirst("X-Trace-Id"));
        assertThat(audit.get("action")).isEqualTo("USER_DISABLED");
        assertThat(audit.get("outcome")).isEqualTo("SUCCESS");
        assertThat(audit.get("created_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT updated_at FROM app_user WHERE id=?",java.sql.Timestamp.class,id(buyer))).isNotNull();
    }
    @Test void auditInsertFailureRollsBackDisableAndRefreshRevocation() throws Exception {
        var buyer=user("BUYER");var admin=user("ADMIN");
        jdbc.execute("ALTER TABLE identity_audit ADD CONSTRAINT fixture_reject_audit CHECK (target_user_id <> "+id(buyer)+")");
        try {
            assertThat(call(HttpMethod.POST,"/api/admin/users/"+id(buyer)+"/disable",admin).getStatusCode().value()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT enabled FROM app_user WHERE id=?",Boolean.class,id(buyer))).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_session WHERE user_id=? AND revoked_at IS NULL",Integer.class,id(buyer))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_audit WHERE target_user_id=?",Integer.class,id(buyer))).isZero();
        } finally {jdbc.execute("ALTER TABLE identity_audit DROP CHECK fixture_reject_audit");}
    }

    // Test-only resources prove the reusable guard against a persisted owner. No production order API/table yet.
    @RestController @Profile("authorization-fixture")
    public static class FixtureEndpoints {
        private final ResourceAccess access;
        private final JdbcTemplate jdbc;
        private final dev.commerceflow.trade.identity.UserAdministration administration;
        public FixtureEndpoints(ResourceAccess access,JdbcTemplate jdbc,dev.commerceflow.trade.identity.UserAdministration administration) {
            this.access=access;this.jdbc=jdbc;this.administration=administration;
        }
        @GetMapping("/api/test-owned-order/{id}") public Map<String,Long> owned(@PathVariable long id) {
            long owner=jdbc.queryForObject("SELECT buyer_id FROM fixture_owned_order WHERE id=?",Long.class,id);
            access.requireOwner(owner);return Map.of("id",id);
        }
        @PostMapping("/api/test-operations") @ResponseStatus(HttpStatus.NO_CONTENT)
        public void operations() {access.requireOperations();}
        @PostMapping("/api/test-disable/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
        public void disable(@PathVariable long id) {administration.disable(id);}
    }
}
