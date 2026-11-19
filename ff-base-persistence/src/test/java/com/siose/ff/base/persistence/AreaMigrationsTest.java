package com.siose.ff.base.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.siose.ff.base.persistence.autoconfigure.AreaMigrationsAutoConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two areas in one database, as in the modulith; one of them alone, as in a service; and an
 * area added to a database that already holds other tables.
 */
class AreaMigrationsTest {

    private final String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    private ApplicationContextRunner runner(MigrationArea... areas) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class, AreaMigrationsAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=" + url);
        for (MigrationArea area : areas) {
            runner = runner.withBean(area.name() + "Area", MigrationArea.class, () -> area);
        }
        return runner;
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // Only the scripts ("type" SQL): an area that starts in a database already holding another
    // area's tables first records a baseline row (version 0), see AreaMigrations.
    @Test
    void eachAreaHasItsOwnHistory() {
        runner(MigrationArea.of("beta"), MigrationArea.of("alpha")).run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(context.getBean(AreaMigrations.class).areas()).extracting(MigrationArea::name)
                    .containsExactly("alpha", "beta");
            assertThat(count(jdbc, "select count(*) from \"alpha_flyway_history\" where \"success\" and \"type\" = 'SQL'")).isEqualTo(1);
            assertThat(count(jdbc, "select count(*) from \"beta_flyway_history\" where \"success\" and \"type\" = 'SQL'")).isEqualTo(2);
            assertThat(count(jdbc, "select count(*) from beta_items")).isZero();
        });
    }

    @Test
    void aServiceWithOneAreaRunsOnlyThatAreaAndAnotherProcessFindsItDone() {
        runner(MigrationArea.of("beta")).run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from information_schema.tables where table_name = 'alpha_items'"))
                    .isZero();
        });
        // The same database, now opened by a process that holds both areas: beta is already done.
        runner(MigrationArea.of("alpha"), MigrationArea.of("beta")).run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from \"beta_flyway_history\" where \"type\" = 'SQL'")).isEqualTo(2);
            assertThat(count(jdbc, "select count(*) from \"alpha_flyway_history\" where \"type\" = 'SQL'")).isEqualTo(1);
        });
    }

    @Test
    void anAreaCanStartInADatabaseThatAlreadyHasTables() {
        runner().run(context -> context.getBean(JdbcTemplate.class).execute("create table someone_elses (id int)"));
        runner(MigrationArea.of("alpha")).run(context -> {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from alpha_items")).isZero();
        });
    }

    @Test
    void theDatabaseIsUsedOnlyAfterTheMigrations() {
        runner(MigrationArea.of("alpha")).run(context -> assertThat(
                context.getBeanFactory().getBeanDefinition("jdbcTemplate").getDependsOn())
                .contains("areaMigrations"));
    }

    @Test
    void canBeSwitchedOff() {
        runner(MigrationArea.of("alpha")).withPropertyValues("ff.migrations.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AreaMigrations.class));
    }

    @Test
    void anAreaNameIsLowerCaseLetters() {
        assertThat(MigrationArea.of("warehousing").historyTable()).isEqualTo("warehousing_flyway_history");
        assertThat(MigrationArea.of("routing").location()).isEqualTo("classpath:db/migration/routing");
        assertThatIllegalArgumentException().isThrownBy(() -> MigrationArea.of("Ware-housing"));
        assertThatIllegalArgumentException().isThrownBy(() -> MigrationArea.of("x"));
    }
}
