package com.siose.ff.base.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Holds the tenant identity of the current request. It is filled by TenantContextFilter from the
 * authenticated caller and cleared when the request ends. There is deliberately no default tenant:
 * code that runs without a tenant must fail instead of silently writing to a shared one.
 */
public final class TenantContext {

    /**
     * Value handed to Hibernate when no tenant is bound (startup, health checks). It never matches
     * stored data, so reads return nothing, and TenantAwareEntity (ff-base-persistence) refuses to persist with it.
     */
    public static final String NO_TENANT = "__no_tenant__";

    private static final ThreadLocal<TenantInfo> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public record TenantInfo(
            String tenantId,
            String username,
            Set<String> roles,
            String authSource
    ) {
        public TenantInfo {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            TenantIds.require(tenantId);
            username = (username == null || username.isBlank()) ? "anonymous" : username;
            roles = (roles == null) ? Set.of() : Set.copyOf(roles);
            authSource = (authSource == null) ? "UNKNOWN" : authSource;
        }

        public boolean hasRole(String role) {
            return roles.contains(role);
        }
    }

    public static void setCurrent(TenantInfo tenantInfo) {
        CURRENT_TENANT.set(Objects.requireNonNull(tenantInfo, "tenantInfo must not be null"));
    }

    public static Optional<TenantInfo> findCurrent() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static TenantInfo requireCurrent() {
        TenantInfo info = CURRENT_TENANT.get();
        if (info == null) {
            throw new TenantAccessException("No tenant is bound to the current request.");
        }
        return info;
    }

    public static String requireTenantId() {
        return requireCurrent().tenantId();
    }

    /** For Hibernate's tenant resolver only; application code should use {@link #requireTenantId()}. */
    public static String currentTenantIdOrNone() {
        TenantInfo info = CURRENT_TENANT.get();
        return info == null ? NO_TENANT : info.tenantId();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
