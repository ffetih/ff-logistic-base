package com.siose.ff.base.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Local-development authentication built from the X-Tenant-Id header.
 * Only ever created when ff.security.header-fallback.enabled=true.
 */
public class TenantHeaderAuthentication extends AbstractAuthenticationToken {

    private final String tenantId;
    private final String username;

    public TenantHeaderAuthentication(
            String tenantId,
            String username,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        this.tenantId = tenantId;
        this.username = username;
        setAuthenticated(true);
    }

    public String getTenantId() {
        return tenantId;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return username;
    }
}
