package com.siose.ff.base.events;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps one process's outbox: publishes unfinished events again, from one pod at a time, and
 * deletes completed ones after a while.
 *
 * <p><b>Again, from one pod.</b> Every pod of the process runs this schedule. Without coordination
 * all of them would publish the same open row at the same moment, and its listener would run once
 * per pod. Each run therefore first takes a PostgreSQL session advisory lock, on one connection,
 * and gives it back at the end; a pod that does not get it skips the run. The lock is named after
 * the schema, so two processes with outboxes of their own never wait for each other. On a
 * database without such locks (the tests' H2) the run goes ahead.
 *
 * <p>Spring Modulith resubmits only rows in status {@code FAILED}: a listener threw. A row that a
 * stopped pod left {@code PUBLISHED} or {@code PROCESSING} turns {@code FAILED} once it is older
 * than {@code spring.modulith.events.staleness.*} (five minutes, {@link EventDefaults}). Which
 * failed rows are due now is decided by {@link EventRetryPolicy}; rows past the maximum attempts
 * are counted in the gauge {@code ff.events.publications.exhausted} and logged when their number
 * grows.
 *
 * <p><b>Completed rows</b> are deleted after the retention, a week by default. Every pod may do
 * that; deleting what another pod deleted already does no harm.
 */
public class OutboxUpkeep {

    private static final Logger log = LoggerFactory.getLogger(OutboxUpkeep.class);

    /** The lock names of all outboxes start here; the schema's hash tells them apart. */
    static final long LOCK_BASE = 5_105_000_000L;

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final OutboxProperties properties;
    private final EventRetryPolicy policy;
    private final AtomicLong exhausted = new AtomicLong();

    public OutboxUpkeep(IncompleteEventPublications incomplete, CompletedEventPublications completed,
            DataSource dataSource, OutboxProperties properties, MeterRegistry meterRegistry) {
        this.incomplete = incomplete;
        this.completed = completed;
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = properties;
        this.policy = properties.retryPolicy();
        if (meterRegistry != null) {
            meterRegistry.gauge("ff.events.publications.exhausted", exhausted);
        }
    }

    long lockKey() {
        return LOCK_BASE + Math.floorMod(properties.schema().hashCode(), 1_000_000);
    }

    @Scheduled(fixedDelayString = "${ff.events.resubmit-delay:PT30S}",
            initialDelayString = "${ff.events.resubmit-delay:PT30S}")
    void resubmitOnSchedule() {
        resubmitOutstanding();
    }

    @Scheduled(fixedDelayString = "${ff.events.cleanup-delay:PT1H}")
    void deleteOldCompletions() {
        log.debug("Deleting completed event publications older than {}", properties.completedRetention());
        completed.deletePublicationsOlderThan(properties.completedRetention());
    }

    /** @return {@code true} when this pod ran the resubmission, {@code false} when another one held the lock */
    public boolean resubmitOutstanding() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection)) {
                log.debug("Another pod is resubmitting events of {}; skipping this run", properties.schema());
                return false;
            }
            try {
                Instant now = Instant.now();
                incomplete.resubmitIncompletePublications(ResubmissionOptions.defaults()
                        .withMinAge(properties.resubmitMinAge())
                        .withBatchSize(properties.batchSize())
                        .withMaxInFlight(properties.maxInFlight())
                        .withFilter(publication -> policy.isDue(publication, now)));
                countExhausted();
                return true;
            } finally {
                unlock(connection);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not lock the event resubmission", ex);
        }
    }

    /** Rows that will not be tried again. */
    long countExhausted() {
        Long count = jdbc.queryForObject("select count(*) from " + properties.schema() + ".event_publication"
                + " where completion_date is null and completion_attempts >= ?", Long.class, policy.maxAttempts());
        long now = count == null ? 0 : count;
        long before = exhausted.getAndSet(now);
        if (now > before) {
            log.warn("{} event publication(s) in {} stopped retrying after {} attempts; see {}.event_publication",
                    now, properties.schema(), policy.maxAttempts(), properties.schema());
        }
        return now;
    }

    private boolean tryLock(Connection connection) throws SQLException {
        return !isPostgres(connection) || call(connection, "select pg_try_advisory_lock(?)");
    }

    private void unlock(Connection connection) throws SQLException {
        if (isPostgres(connection)) {
            call(connection, "select pg_advisory_unlock(?)");
        }
    }

    private boolean call(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lockKey());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static boolean isPostgres(Connection connection) throws SQLException {
        return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
    }
}
