package dev.commerceflow.trade.identity;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean UserDetailsService userDetailsService(ObjectProvider<UserRepository> repositories) {
        return name -> {
            var repository = repositories.getIfAvailable();
            if (repository == null) throw new UsernameNotFoundException("Invalid credentials");
            var user = repository.findByLogin(name).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return new UserPrincipal(user.id(), user.loginName(), user.passwordHash(), user.role(), user.enabled());
        };
    }

    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<org.springframework.security.oauth2.jwt.JwtDecoder> decoders,
            ObjectProvider<UserRepository> repositories) throws Exception {
        // Bearer header / refresh JSON body only; no browser auth cookies or sessions.
        var decoder = decoders.getIfAvailable();
        if (decoder != null) {
            http.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(decoder))
                .authenticationEntryPoint((req,res,ex) -> unauthorized(res)));
            http.addFilterAfter(new ActiveUserFilter(repositories.getObject()),
                org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        }
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> {
                    unauthorized(res);
                })
                .accessDeniedHandler((req, res, ex) -> {
                    ErrorResponses.write(res, 403, "FORBIDDEN", "Access denied");
                }))
            .build();
    }
    static void unauthorized(jakarta.servlet.http.HttpServletResponse res) throws java.io.IOException {
        ErrorResponses.write(res, 401, "UNAUTHORIZED", "Authentication required");
    }
}
