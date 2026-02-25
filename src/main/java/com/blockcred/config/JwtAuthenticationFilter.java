package com.blockcred.config;

import com.blockcred.domain.AuthRole;
import com.blockcred.service.ApiAccessService;
import com.blockcred.service.AuthPrincipal;
import com.blockcred.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final ApiAccessService apiAccessService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, ApiAccessService apiAccessService) {
        this.jwtTokenService = jwtTokenService;
        this.apiAccessService = apiAccessService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring("Bearer ".length()).trim();
                jwtTokenService.parseAndVerify(token).ifPresent(claims -> {
                    AuthPrincipal principal = new AuthPrincipal(
                            claims.userId(),
                            claims.username(),
                            claims.displayName(),
                            claims.roles(),
                            false
                    );
                    setAuthentication(principal);
                });
            } else if (apiAccessService.isLegacyHeaderEnabled()) {
                String adminToken = request.getHeader("X-Admin-Token");
                if (apiAccessService.matchesAdminToken(adminToken)) {
                    setAuthentication(new AuthPrincipal(
                            -1L,
                            "legacy-admin",
                            "Legacy Admin",
                            Set.of(AuthRole.ADMIN, AuthRole.ISSUER),
                            true
                    ));
                } else {
                    String issuerToken = request.getHeader("X-Issuer-Token");
                    if (apiAccessService.matchesIssuerToken(issuerToken)) {
                        setAuthentication(new AuthPrincipal(
                                -2L,
                                "legacy-issuer",
                                "Legacy Issuer",
                                Set.of(AuthRole.ISSUER),
                                true
                        ));
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(AuthPrincipal principal) {
        Collection<? extends GrantedAuthority> authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
