package com.siose.ff.base.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The outbox of one process ({@code ff.events.*}). Only {@code schema} must be set; it switches
 * the outbox on.
 *
 * @param schema             the process's own schema for {@code event_publication}: {@code modulith},
 *                           or the service's name
 * @param resubmitDelay      how often a pod tries to take the lock and publish unfinished events again
 * @param resubmitMinAge     the first wait after a failed attempt
 * @param resubmitMaxDelay   the wait doubles after each attempt, up to this
 * @param maxAttempts        then the row stays open, for a person to look at
 * @param batchSize          unfinished rows read per run
 * @param maxInFlight        resubmitted events being handled at once
 * @param completedRetention completed rows are deleted after this
 * @param cleanupDelay       how often completed rows are deleted
 */
@ConfigurationProperties("ff.events")
public record OutboxProperties(
        String schema,
        Duration resubmitDelay,
        Duration resubmitMinAge,
        Duration resubmitMaxDelay,
        Integer maxAttempts,
        Integer batchSize,
        Integer maxInFlight,
        Duration completedRetention,
        Duration cleanupDelay) {

    public OutboxProperties {
        resubmitDelay = resubmitDelay == null ? Duration.ofSeconds(30) : resubmitDelay;
        resubmitMinAge = resubmitMinAge == null ? Duration.ofMinutes(1) : resubmitMinAge;
        resubmitMaxDelay = resubmitMaxDelay == null ? Duration.ofHours(1) : resubmitMaxDelay;
        maxAttempts = maxAttempts == null ? 5 : maxAttempts;
        batchSize = batchSize == null ? 100 : batchSize;
        maxInFlight = maxInFlight == null ? 50 : maxInFlight;
        completedRetention = completedRetention == null ? Duration.ofDays(7) : completedRetention;
        cleanupDelay = cleanupDelay == null ? Duration.ofHours(1) : cleanupDelay;
    }

    public EventRetryPolicy retryPolicy() {
        return new EventRetryPolicy(maxAttempts, resubmitMinAge, resubmitMaxDelay);
    }
}
