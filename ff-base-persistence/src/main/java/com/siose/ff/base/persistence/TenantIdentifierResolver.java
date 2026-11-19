package com.siose.ff.base.persistence;

import com.siose.ff.base.tenant.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which tenant the current unit of work belongs to. Together with @TenantId on
 * {@link TenantAwareEntity}, Hibernate adds "tenant_id = ?" to every query and load by id, so a
 * forgotten filter in a repository can no longer leak another tenant's rows.
 * Registered per service with spring.jpa.properties.hibernate.tenant_identifier_resolver.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.currentTenantIdOrNone();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
