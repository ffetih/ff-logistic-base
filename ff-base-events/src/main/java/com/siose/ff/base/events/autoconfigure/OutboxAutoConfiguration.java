package com.siose.ff.base.events.autoconfigure;

import com.siose.ff.base.events.OutboxProperties;
import com.siose.ff.base.events.OutboxUpkeep;
import com.siose.ff.base.persistence.MigrationArea;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The outbox of a process that sets {@code ff.events.schema} (decision 12). Such a process has the
 * event publication registry (spring-modulith-starter-jdbc); without it, it fails to start:
 *
 * <ul>
 *   <li>the area {@code outbox}, in that schema: {@code event_publication} is migrated with the
 *       process's other areas, also by the migration Job;</li>
 *   <li>{@link OutboxUpkeep}, not in the migration Job ({@code migrate} profile), which only
 *       migrates.</li>
 * </ul>
 */
@AutoConfiguration(afterName = {"org.springframework.modulith.events.config.EventPublicationAutoConfiguration",
        "org.springframework.modulith.events.jdbc.JdbcEventPublicationAutoConfiguration"})
@ConditionalOnProperty(name = "ff.events.schema")
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    /** The area's name; its history table is {@code <schema>.outbox_flyway_history}. */
    public static final String AREA = "outbox";

    @Bean
    MigrationArea outboxMigrations(OutboxProperties properties) {
        return MigrationArea.of(AREA, properties.schema());
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("!migrate")
    @EnableScheduling
    static class Upkeep {

        @Bean
        OutboxUpkeep outboxUpkeep(IncompleteEventPublications incomplete, CompletedEventPublications completed,
                DataSource dataSource, OutboxProperties properties, ObjectProvider<MeterRegistry> meters) {
            return new OutboxUpkeep(incomplete, completed, dataSource, properties, meters.getIfAvailable());
        }
    }
}
