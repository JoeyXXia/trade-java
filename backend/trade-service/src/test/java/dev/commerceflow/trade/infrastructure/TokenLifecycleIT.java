package dev.commerceflow.trade.infrastructure;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("database") @Testcontainers
class TokenLifecycleIT {
    private static final String PASSWORD="fixture-password-123";
    private static final byte[] KEY=new byte[64];
    static { new java.security.SecureRandom().nextBytes(KEY); }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>(TestImages.locked("MYSQL_IMAGE","mysql"))
        .withDatabaseName("cf_tokens").withUsername("token_test").withPassword("container-only-test-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        if(!MYSQL.isRunning()) MYSQL.start();
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
            .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);
        p.add("spring.datasource.username",MYSQL::getUsername);
        p.add("spring.datasource.password",MYSQL::getPassword);
        p.add("commerceflow.jwt-secret-base64",()->Base64.getEncoder().encodeToString(KEY));
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    ResponseEntity<String> post(String action,Object body) { return http.postForEntity("/api/auth/"+action,body,String.class); }
    JsonNode node(ResponseEntity<String> response) throws Exception { return json.readTree(response.getBody()); }
    String name() { return "u"+UUID.randomUUID().toString().replace("-",""); }
    JsonNode login(String login) throws Exception {
        assertThat(post("register",Map.of("loginName",login,"password",PASSWORD)).getStatusCode().value()).isEqualTo(201);
        var response=post("login",Map.of("loginName",login,"password",PASSWORD));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        return node(response);
    }
    ResponseEntity<String> get(String path,String token) {
        var headers=new HttpHeaders();headers.setBearerAuth(token);
        return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers),String.class);
    }
    ResponseEntity<String> refresh(String token) { return post("refresh",Map.of("refreshToken",token)); }

    @Test void bearerAuthenticationAndRotationWorkWithoutStoringRawRefresh() throws Exception {
        var first=login(name());
        String old=first.path("refreshToken").asText();
        assertThat(first.path("expiresIn").asLong()).isEqualTo(300);
        assertThat(get("/api/me",first.path("accessToken").asText()).getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_session WHERE token_hash=?",Integer.class,old)).isZero();
        String stored=jdbc.queryForObject("SELECT token_hash FROM refresh_session WHERE user_id=?",String.class,first.path("user").path("id").asLong());
        assertThat(stored).hasSize(64).isNotEqualTo(old);
        var second=refresh(old);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        String successor=node(second).path("refreshToken").asText();
        assertThat(successor).hasSize(43).isNotEqualTo(old);
        assertThat(refresh(old).getStatusCode().value()).isEqualTo(401);
        assertThat(refresh(successor).getStatusCode().value()).isEqualTo(401); // Reuse revokes family and commits.
    }
    @Test void logoutRevokesRefreshAndDocumentsRemainingAccessWindow() throws Exception {
        var token=login(name());
        assertThat(post("logout",Map.of("refreshToken",token.path("refreshToken").asText())).getStatusCode().value()).isEqualTo(204);
        assertThat(refresh(token.path("refreshToken").asText()).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/me",token.path("accessToken").asText()).getStatusCode().value()).isEqualTo(200);
        assertThat(post("logout",Map.of("refreshToken",token.path("refreshToken").asText())).getStatusCode().value()).isEqualTo(204);
    }
    @Test void expiredRefreshIsRejected() throws Exception {
        var token=login(name());
        jdbc.update("UPDATE refresh_session SET expires_at=? WHERE user_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),token.path("user").path("id").asLong());
        assertThat(refresh(token.path("refreshToken").asText()).getStatusCode().value()).isEqualTo(401);
    }
    @Test void concurrentRefreshHasExactlyOneWinnerAndReplayRevokesFamily() throws Exception {
        var token=login(name());String old=token.path("refreshToken").asText();
        var start=new CyclicBarrier(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=IntStream.range(0,2).mapToObj(i->pool.submit(()->{start.await(10,TimeUnit.SECONDS);return refresh(old);})).toList();
            var responses=new ArrayList<ResponseEntity<String>>();
            for(var task:tasks) responses.add(task.get(20,TimeUnit.SECONDS));
            assertThat(responses.stream().map(r->r.getStatusCode().value()).toList()).containsExactlyInAnyOrder(200,401);
            String successor=node(responses.stream().filter(r->r.getStatusCode().value()==200).findFirst().orElseThrow()).path("refreshToken").asText();
            assertThat(refresh(successor).getStatusCode().value()).isEqualTo(401);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_session WHERE user_id=?",Integer.class,token.path("user").path("id").asLong())).isEqualTo(2);
    }
    @Test void disabledUserCannotLoginRefreshOrUseAnExistingAccessToken() throws Exception {
        var buyer=login(name());var admin=login(name());
        long adminId=admin.path("user").path("id").asLong();
        jdbc.update("UPDATE app_user SET role='ADMIN' WHERE id=?",adminId); // Fixture only; public API cannot grant roles.
        var headers=new HttpHeaders();headers.setBearerAuth(admin.path("accessToken").asText());
        var disabled=http.exchange("/api/admin/users/"+buyer.path("user").path("id").asText()+"/disable",HttpMethod.POST,new HttpEntity<>(headers),String.class);
        assertThat(disabled.getStatusCode().value()).isEqualTo(204);
        assertThat(get("/api/me",buyer.path("accessToken").asText()).getStatusCode().value()).isEqualTo(401);
        assertThat(refresh(buyer.path("refreshToken").asText()).getStatusCode().value()).isEqualTo(401);
        assertThat(post("login",Map.of("loginName",buyer.path("user").path("loginName").asText(),"password",PASSWORD)).getStatusCode().value()).isEqualTo(401);
    }
    @Test void buyerCannotDisableAnotherUser() throws Exception {
        var token=login(name());
        var h=new HttpHeaders();h.setBearerAuth(token.path("accessToken").asText());
        assertThat(http.exchange("/api/admin/users/"+token.path("user").path("id").asText()+"/disable",HttpMethod.POST,new HttpEntity<>(h),String.class).getStatusCode().value()).isEqualTo(403);
    }
    @Test void signedButInvalidClaimsAndAlgorithmAreRejected() throws Exception {
        var user=login(name());String id=user.path("user").path("id").asText();
        for(var test:List.of(
            mint(id,"wrong-issuer","commerceflow-web",Instant.now().plusSeconds(300),MacAlgorithm.HS256),
            mint(id,"commerceflow-trade","wrong-audience",Instant.now().plusSeconds(300),MacAlgorithm.HS256),
            mint(id,"commerceflow-trade",null,Instant.now().plusSeconds(300),MacAlgorithm.HS256),
            mint(id,"commerceflow-trade","commerceflow-web",Instant.now().minusSeconds(120),MacAlgorithm.HS256),
            mint(id,"commerceflow-trade","commerceflow-web",Instant.now().plusSeconds(300),MacAlgorithm.HS384))) {
            assertThat(get("/api/me",test).getStatusCode().value()).isEqualTo(401);
        }
        String good=user.path("accessToken").asText();int at=good.lastIndexOf('.')+8;
        String corrupt=good.substring(0,at)+(good.charAt(at)=='A'?'B':'A')+good.substring(at+1);
        assertThat(get("/api/me",corrupt).getStatusCode().value()).isEqualTo(401);
        assertThat(http.getForEntity("/api/me",String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/me",good).getStatusCode().value()).isEqualTo(200); // No request context leak.
    }
    @Test void failuresAreRateLimitedPerAccountAndActualSource() {
        String target=name();
        for(int i=0;i<10;i++) assertThat(post("login",Map.of("loginName",target,"password","wrong-password-123")).getStatusCode().value()).isEqualTo(401);
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For","203.0.113.9");
        var blocked=http.postForEntity("/api/auth/login",new HttpEntity<>(Map.of("loginName",target,"password","wrong-password-123"),headers),String.class);
        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(post("login",Map.of("loginName",name(),"password","wrong-password-123")).getStatusCode().value()).isEqualTo(401);
    }
    String mint(String id,String issuer,String audience,Instant expiry,MacAlgorithm algorithm) {
        var claims=JwtClaimsSet.builder().issuer(issuer).subject(id).issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(expiry).id(UUID.randomUUID().toString());
        if(audience!=null) claims.audience(List.of(audience));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(),claims.build())).getTokenValue();
    }
}
