package com.siose.ff.base.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class TenantJwtAuthenticationConverterTest {

    @Test
    void shouldMapKeycloakTokenToTenantAndRoleAuthorities() {
        Jwt jwt = Jwt.withTokenValue("verified-by-decoder")
                .header("alg", "RS256")
                .subject("u-1001")
                .claim("preferred_username", "planner.ff")
                .claim("tenant_id", "tenant-a")
                .claim("realm_access", Map.of("roles", List.of("PLANNER", "TENANT_ADMIN")))
                .claim("resource_access", Map.of("product-api", Map.of("roles", List.of("VIEWER"))))
                .build();

        AbstractAuthenticationToken authentication = new TenantJwtAuthenticationConverter("tenant_id").convert(jwt);

        assertThat(authentication.getName()).isEqualTo("planner.ff");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PLANNER", "ROLE_TENANT_ADMIN", "ROLE_VIEWER");
    }

    @Test
    void shouldRejectTokenWithoutTenantIdClaim() {
        Jwt jwt = Jwt.withTokenValue("verified-by-decoder")
                .header("alg", "RS256")
                .subject("u-1001")
                .claim("preferred_username", "user1")
                .build();

        assertThatThrownBy(() -> new TenantJwtAuthenticationConverter("tenant_id").convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("tenant_id");
    }

    @Test
    void shouldRejectTokenWithATenantIdThatBreaksTheFormat() {
        Jwt jwt = Jwt.withTokenValue("verified-by-decoder")
                .header("alg", "RS256")
                .subject("u-1001")
                .claim("tenant_id", "FF.EU Freight")
                .build();

        assertThatThrownBy(() -> new TenantJwtAuthenticationConverter("tenant_id").convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("invalid");
    }
}
