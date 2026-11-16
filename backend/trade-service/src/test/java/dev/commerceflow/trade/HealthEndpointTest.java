package dev.commerceflow.trade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {
    @Autowired TestRestTemplate http;

    @Test
    void healthIsUpWithoutMiddleware() {
        var response = http.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void diagnosticsAreNotExposed() {
        assertThat(http.getForEntity("/actuator/env", String.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/actuator/beans", String.class).getStatusCode().value()).isEqualTo(404);
    }
}
