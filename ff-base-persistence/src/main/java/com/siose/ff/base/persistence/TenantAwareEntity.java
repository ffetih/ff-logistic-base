package com.siose.ff.base.persistence;

import com.siose.ff.base.tenant.TenantAccessException;
import com.siose.ff.base.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/**
 * Base JPA mapped superclass enforcing tenant isolation in every product built on base.
 * The @TenantId column is filled and filtered by Hibernate itself (see TenantIdentifierResolver).
 */
@MappedSuperclass
public abstract class TenantAwareEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onPrePersist() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
        if (this.tenantId == null || this.tenantId.isBlank()) {
            this.tenantId = TenantContext.requireTenantId();
        }
        if (TenantContext.NO_TENANT.equals(this.tenantId)) {
            throw new TenantAccessException("Refusing to persist " + getClass().getSimpleName() + " without a tenant.");
        }
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
