package com.siose.ff.base.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One process's outbox, end to end: the table in the process's schema, migrated as an area; the
 * settings that follow from the schema; a listener that fails, and the upkeep that delivers its
 * event again until the maximum attempts.
 */
@SpringBootTest(classes = OutboxTest.TestApplication.class)
class OutboxTest {

    record SomethingHappened(UUID id) {
    }

    /** Fails a given number of times per event, then succeeds; counts every call. */
    static class FailingListener {

        private final Map<UUID, Integer> failuresLeft = new ConcurrentHashMap<>();
        private final Map<UUID, AtomicInteger> calls = new ConcurrentHashMap<>();

        public void failFirst(UUID id, int times) {
            failuresLeft.put(id, times);
        }

        public int calls(UUID id) {
            return calls.getOrDefault(id, new AtomicInteger()).get();
        }

        @Async
        @TransactionalEventListener
        public void on(SomethingHappened event) {
            calls.computeIfAbsent(event.id(), id -> new AtomicInteger()).incrementAndGet();
            int left = failuresLeft.getOrDefault(event.id(), 0);
            if (left > 0) {
                failuresLeft.put(event.id(), left - 1);
                throw new IllegalStateException("Listener down (test)");
            }
        }
    }

    @SpringBootApplication
    static class TestApplication {

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private FailingListener listener;

    @Autowired
    private OutboxUpkeep upkeep;

    @Autowired
    private DataSource dataSource;

    private UUID published(int failures) {
        UUID id = UUID.randomUUID();
        listener.failFirst(id, failures);
        new TransactionTemplate(transactions).executeWithoutResult(status -> events.publishEvent(new SomethingHappened(id)));
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select status, completion_attempts, completion_date from testbox.event_publication"
                + " where serialized_event like ?", "%" + id + "%");
    }

    @Test
    void theOutboxIsInTheProcesssOwnSchemaMigratedAsAnArea() {
        assertThat(jdbc.queryForObject("select max(\"version\") from testbox.\"outbox_flyway_history\""
                + " where \"type\" = 'SQL'", String.class)).isEqualTo("1");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables"
                + " where table_name = 'event_publication' and table_schema <> 'testbox'", Integer.class)).isZero();
        assertThat(environment.getProperty("spring.modulith.events.jdbc.schema")).isEqualTo("testbox");
        assertThat(environment.getProperty("spring.modulith.events.republish-outstanding-events-on-restart"))
                .isEqualTo("false");
    }

    @Test
    void aFailedListenerGetsTheEventAgain() {
        UUID id = published(1);
        await().atMost(Duration.ofSeconds(10)).until(() -> "FAILED".equals(row(id).get("status")));

        assertThat(upkeep.resubmitOutstanding()).isTrue();

        await().atMost(Duration.ofSeconds(10)).until(() -> row(id).get("completion_date") != null);
        assertThat(listener.calls(id)).isEqualTo(2);
        assertThat(((Number) row(id).get("completion_attempts")).intValue()).isEqualTo(2);
    }

    @Test
    void afterTheMaximumAttemptsTheRowStaysOpen() {
        UUID id = published(10);
        await().atMost(Duration.ofSeconds(10)).until(() -> "FAILED".equals(row(id).get("status")));

        for (int run = 0; run < 4; run++) {
            int before = listener.calls(id);
            upkeep.resubmitOutstanding();
            if (before < 3) {
                await().atMost(Duration.ofSeconds(10)).until(() -> listener.calls(id) > before
                        && "FAILED".equals(row(id).get("status")));
            }
        }

        // Attempt 1 by the publication, 2 and 3 by resubmissions; then no more.
        assertThat(listener.calls(id)).isEqualTo(3);
        assertThat(row(id).get("completion_date")).isNull();
        assertThat(upkeep.countExhausted()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void eachOutboxHasALockOfItsOwn() {
        OutboxProperties modulith = new OutboxProperties("modulith", null, null, null, null, null, null, null, null);
        OutboxProperties transportation = new OutboxProperties("transportation", null, null, null, null, null, null,
                null, null);
        assertThat(new OutboxUpkeep(null, null, dataSource, modulith, null).lockKey())
                .isNotEqualTo(new OutboxUpkeep(null, null, dataSource, transportation, null).lockKey());
    }
}
