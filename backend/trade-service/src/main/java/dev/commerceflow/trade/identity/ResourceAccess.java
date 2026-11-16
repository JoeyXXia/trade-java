package dev.commerceflow.trade.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("resourceAccess") @Profile("database")
public class ResourceAccess {
    public boolean owns(Authentication authentication, long ownerId) {
        return authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()
            && Long.toString(ownerId).equals(jwt.getToken().getSubject());
    }
    // ownerId must come from the resource's persisted owner, never an HTTP userId field.
    // Invoke through this injected bean so method security runs through its proxy.
    @PreAuthorize("@resourceAccess.owns(authentication, #p0)")
    public void requireOwner(long ownerId) {}

    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public void requireOperations() {}
}
