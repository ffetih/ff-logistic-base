package com.siose.ff.base.persistence.autoconfigure;

import com.siose.ff.base.persistence.AreaMigrations;
import com.siose.ff.base.persistence.MigrationArea;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Runs the migrations of every {@link MigrationArea} bean, each with its own history table.
 * Off with {@code ff.migrations.enabled=false}. Spring Boot's own Flyway auto-configuration is
 * not used for the areas: an application that wants it (one location, one history table) brings
 * {@code spring-boot-starter-flyway} itself.
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(Flyway.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(name = "ff.migrations.enabled", matchIfMissing = true)
public class AreaMigrationsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AreaMigrations areaMigrations(DataSource dataSource, ObjectProvider<MigrationArea> areas) {
        return new AreaMigrations(dataSource, areas.orderedStream().toList());
    }
}
