package dev.commerceflow.probe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.configuration.CompatibilityVerifierAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityRejectionTest {
    @Test
    void incompatibleBootVersionFailsInsteadOfDisablingTheVerifier() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CompatibilityVerifierAutoConfiguration.class))
            .withPropertyValues("spring.cloud.compatibility-verifier.enabled=true",
                "spring.cloud.compatibility-verifier.compatible-boot-versions=0.0.x")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining(
                    "org.springframework.cloud.configuration.CompatibilityNotMetException");
            });
    }
}
