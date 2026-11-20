package com.siose.ff.base.persistence;

import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Migrates every {@link MigrationArea} of the application at startup, one Flyway run per area,
 * in name order. Areas share no tables, so the order does not matter for correctness; a fixed
 * order only keeps the log the same from one start to the next.
 *
 * <p>The second area to arrive in a database finds other areas' tables there. Flyway would
 * refuse that schema; with {@code baselineOnMigrate} it records a baseline row (version 0) in the
 * area's history table and then applies the area's scripts from V1.
 *
 * <p>Spring Boot treats this bean as a database initializer
 * ({@code AreaMigrationsDatabaseInitializerDetector}): JPA, {@code JdbcTemplate} and everything
 * else that uses the database is created after it has run.
 */
public class AreaMigrations implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(AreaMigrations.class);

    private final DataSource dataSource;
    private final List<MigrationArea> areas;

    public AreaMigrations(DataSource dataSource, List<MigrationArea> areas) {
        this.dataSource = dataSource;
        this.areas = areas.stream().sorted(Comparator.comparing(MigrationArea::name)).toList();
    }

    public List<MigrationArea> areas() {
        return areas;
    }

    @Override
    public void afterPropertiesSet() {
        for (MigrationArea area : areas) {
            FluentConfiguration flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(area.location())
                    .table(area.historyTable())
                    // A history table of its own in a database that already has other tables:
                    // without this Flyway would refuse to start an area in a non-empty schema.
                    .baselineOnMigrate(true)
                    .baselineVersion("0");
            if (area.schema() != null) {
                // Created if missing; the scripts and the history table live in it.
                flyway.schemas(area.schema());
            }
            MigrateResult result = flyway.load().migrate();
            log.info("Area {}{}: {} migration(s) applied, now at version {}", area.name(),
                    area.schema() == null ? "" : " (schema " + area.schema() + ")",
                    result.migrationsExecuted, result.targetSchemaVersion);
        }
    }
}
