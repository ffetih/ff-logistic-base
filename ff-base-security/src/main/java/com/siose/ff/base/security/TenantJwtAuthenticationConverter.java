package com.siose.ff.base.security;

import com.siose.ff.base.tenant.TenantIds;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Maps an already verified access token to a Spring Security authentication.
 * Signature, expiry, issuer and audience are checked by the JwtDecoder before this runs;
 * this class requires the tenant claim and turns the role claims into ROLE_* authorities.
 *
 * <p>Role claims follow the layout of the tokens the identity service hands out
 * (Keycloak format): {@code realm_access.roles} and {@code resource_access.<client>.roles}.
 * Products never call Keycloak; they only read this claim layout.
 */
public class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String tenantClaim;

    public TenantJwtAuthenticationConverter(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String tenantId = jwt.getClaimAsString(tenantClaim);
        if (tenantId == null || tenantId.isBlank()) {
            throw new InvalidBearerTokenException(
                    "Access token is missing the required '" + tenantClaim + "' claim");
        }
        if (!TenantIds.isValid(tenantId)) {
            throw new InvalidBearerTokenException("Access token has an invalid '" + tenantClaim + "' claim");
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        addRoles(authorities, jwt.getClaimAsMap("realm_access"));
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            for (Object clientAccess : resourceAccess.values()) {
                if (clientAccess instanceof Map<?, ?> clientMap) {
                    addRoles(authorities, clientMap);
                }
            }
        }

        String username = jwt.getClaimAsString("preferred_username");
        return new JwtAuthenticationToken(jwt, authorities, username != null ? username : jwt.getSubject());
    }

    private static void addRoles(Set<GrantedAuthority> authorities, Map<?, ?> access) {
        if (access != null && access.get("roles") instanceof Collection<?> roles) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
    }
}
