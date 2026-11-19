package com.siose.ff.base.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform security settings ({@code ff.security.*}).
 */
@ConfigurationProperties(prefix = "ff.security")
public class TenantSecurityProperties {

    /** Name of the access-token claim that carries the tenant identifier. */
    private String tenantClaim = "tenant_id";

    private final HeaderFallback headerFallback = new HeaderFallback();

    public String getTenantClaim() {
        return tenantClaim;
    }

    public void setTenantClaim(String tenantClaim) {
        this.tenantClaim = tenantClaim;
    }

    public HeaderFallback getHeaderFallback() {
        return headerFallback;
    }

    /**
     * Local development only: accept X-Tenant-Id / X-User-Name / X-User-Roles headers instead of
     * an access token. Off by default; a product enables it in its "local" profile.
     */
    public static class HeaderFallback {

        private boolean enabled = false;

        /**
         * Roles granted when a request has X-Tenant-Id but no X-User-Roles. Empty by default, so such
         * a request is authenticated but may do nothing; a product names its narrowest role here.
         */
        private List<String> defaultRoles = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getDefaultRoles() {
            return defaultRoles;
        }

        public void setDefaultRoles(List<String> defaultRoles) {
            this.defaultRoles = defaultRoles;
        }
    }
}
