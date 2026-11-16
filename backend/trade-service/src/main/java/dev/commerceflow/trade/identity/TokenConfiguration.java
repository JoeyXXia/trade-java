package dev.commerceflow.trade.identity;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods = false) @Profile("database")
public class TokenConfiguration {
    static final String ISSUER = "commerceflow-trade";
    static final String AUDIENCE = "commerceflow-web";
    static SecretKeySpec key(String value) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT key must be valid Base64"); }
        if (bytes.length < 32) throw new IllegalStateException("JWT key must contain at least 32 random bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(@Value("${commerceflow.jwt-secret-base64}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(secret)));
    }
    @Bean JwtDecoder jwtDecoder(@Value("${commerceflow.jwt-secret-base64}") String secret) {
        var decoder = NimbusJwtDecoder.withSecretKey(key(secret)).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid = jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null && jwt.getId() != null;
            if (valid) valid = !jwt.getIssuedAt().isAfter(java.time.Instant.now().plusSeconds(30))
                && jwt.getExpiresAt().isAfter(jwt.getIssuedAt());
            try { valid &= Long.parseLong(jwt.getSubject()) > 0; }
            catch (RuntimeException e) { valid = false; }
            return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ofSeconds(30)), new JwtIssuerValidator(ISSUER), claims));
        return decoder;
    }
}
