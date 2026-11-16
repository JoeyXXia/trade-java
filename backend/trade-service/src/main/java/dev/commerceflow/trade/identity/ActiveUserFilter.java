package dev.commerceflow.trade.identity;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

final class ActiveUserFilter extends OncePerRequestFilter {
    private final UserRepository users;
    ActiveUserFilter(UserRepository users) { this.users = users; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            try {
                var user = users.findById(Long.parseLong(token.getToken().getSubject()));
                if (user.isEmpty() || !user.get().enabled()) {
                    SecurityContextHolder.clearContext();
                    SecurityConfiguration.unauthorized(response);
                    return;
                }
                var fresh = new JwtAuthenticationToken(token.getToken(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role())));
                SecurityContextHolder.getContext().setAuthentication(fresh);
            } catch (DataAccessException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(503); response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\"Service temporarily unavailable\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
