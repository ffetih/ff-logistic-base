package com.siose.ff.base.persistence.autoconfigure;

import com.siose.ff.base.persistence.DatabaseSequences;
import com.siose.ff.base.persistence.TenantIdentifierResolver;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tenant-aware persistence: Hibernate adds {@code tenant_id = ?} to every query of a
 * {@code TenantAwareEntity}, and business numbers come from database sequences.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        beforeName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@ConditionalOnClass(JdbcTemplate.class)
public class TenantPersistenceAutoConfiguration {

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        TenantIdentifierResolver resolver = new TenantIdentifierResolver();
        return (Map<String, Object> hibernateProperties) ->
                hibernateProperties.put("hibernate.tenant_identifier_resolver", resolver);
    }

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean
    DatabaseSequences databaseSequences(JdbcTemplate jdbcTemplate) {
        return new DatabaseSequences(jdbcTemplate);
    }
}
