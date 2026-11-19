package com.siose.ff.base.security;

import com.siose.ff.base.tenant.TenantContext;
import com.siose.ff.base.tenant.TenantContext.TenantInfo;
import com.siose.ff.base.tenant.TenantIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs inside the Spring Security filter chain, after authentication and before authorization.
 * It copies the tenant of the authenticated caller into {@link TenantContext} for the duration
 * of the request. The tenant always comes from the verified token, never from request data,
 * unless the local-development header fallback is switched on.
 *
 * <p>A tenant id that breaks {@link TenantIds} is never bound: a header with one does not
 * authenticate, and a token with one leaves the request without a tenant (403). Real tokens are
 * already refused by {@link TenantJwtAuthenticationConverter} with 401.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private final TenantSecurityProperties properties;

    public TenantContextFilter(TenantSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (isUnauthenticated(authentication) && properties.getHeaderFallback().isEnabled()) {
                authentication = authenticateFromHeaders(request);
            }

            TenantInfo tenantInfo = toTenantInfo(authentication);
            if (tenantInfo != null) {
                TenantContext.setCurrent(tenantInfo);
                MDC.put("tenantId", tenantInfo.tenantId());
                MDC.put("user", tenantInfo.username());
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            MDC.remove("user");
        }
    }

    private static boolean isUnauthenticated(Authentication authentication) {
        return authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
    }

    private Authentication authenticateFromHeaders(HttpServletRequest request) {
        String tenantId = request.getHeader(HEADER_TENANT_ID);
        if (tenantId == null || !TenantIds.isValid(tenantId.trim())) {
            return null;
        }
        String username = request.getHeader(HEADER_USER_NAME);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);
        List<String> roles = (rolesHeader == null || rolesHeader.isBlank())
                ? properties.getHeaderFallback().getDefaultRoles()
                : Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        TenantHeaderAuthentication authentication = new TenantHeaderAuthentication(
                tenantId.trim(),
                (username == null || username.isBlank()) ? "local-dev" : username.trim(),
                authorities
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        return authentication;
    }

    private TenantInfo toTenantInfo(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String tenantId = jwtAuthentication.getToken().getClaimAsString(properties.getTenantClaim());
            if (!TenantIds.isValid(tenantId)) {
                return null;
            }
            return new TenantInfo(tenantId, jwtAuthentication.getName(), roleNames(authentication), "JWT");
        }
        if (authentication instanceof TenantHeaderAuthentication headerAuthentication) {
            return new TenantInfo(
                    headerAuthentication.getTenantId(),
                    headerAuthentication.getName(),
                    roleNames(authentication),
                    "TENANT_HEADER_LOCAL_DEV"
            );
        }
        return null;
    }

    private static Set<String> roleNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring("ROLE_".length()) : authority)
                .collect(Collectors.toSet());
    }
}
