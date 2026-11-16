package dev.commerceflow.probe;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CompatibilityTest.Probe.class, properties = {
    "spring.application.name=compatibility-probe",
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false"
})
class CompatibilityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Probe {}

    @Test
    void clientsLinkAndCloudCompatibilityVerifierAcceptsBoot() throws Exception {
        assertThat(NamingService.class.getName()).contains("nacos");
        assertThat(NacosFactory.class.getName()).contains("nacos");
        var producer = new DefaultMQProducer("day1-linkage-only");
        assertThat(producer.getProducerGroup()).isEqualTo("day1-linkage-only");
        // No network success is claimed by this test. Real services are tested separately.
    }
}
