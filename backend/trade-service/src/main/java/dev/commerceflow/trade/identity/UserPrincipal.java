package dev.commerceflow.trade.identity;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(long id, String loginName, String passwordHash, String role) implements UserDetails {
    @Override public String getUsername() { return loginName; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
    @Override public String toString() { return "UserPrincipal[id=" + id + ", role=" + role + "]"; }
}
