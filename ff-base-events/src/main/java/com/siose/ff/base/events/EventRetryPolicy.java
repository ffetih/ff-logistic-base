package com.siose.ff.base.events;

import java.time.Duration;
import java.time.Instant;
import org.springframework.modulith.events.EventPublication;

/**
 * Whether an unfinished event is tried again now.
 *
 * <ul>
 *   <li>After {@code maxAttempts} attempts, never again: a listener that keeps failing would only
 *       add load and log noise. The row stays open in {@code event_publication} for a person.</li>
 *   <li>Before that, the wait since the last attempt doubles: {@code firstDelay}, 2×, 4×, … and
 *       never more than {@code maxDelay}.</li>
 * </ul>
 *
 * Spring Modulith counts the first delivery as attempt 1, and each resubmission as one more.
 */
public record EventRetryPolicy(int maxAttempts, Duration firstDelay, Duration maxDelay) {

    public EventRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (firstDelay.isNegative() || maxDelay.isNegative()) {
            throw new IllegalArgumentException("Retry delays must not be negative");
        }
    }

    public boolean isExhausted(EventPublication publication) {
        return publication.getCompletionAttempts() >= maxAttempts;
    }

    public boolean isDue(EventPublication publication, Instant now) {
        if (isExhausted(publication)) {
            return false;
        }
        Instant lastAttempt = publication.getLastResubmissionDate() != null
                ? publication.getLastResubmissionDate()
                : publication.getPublicationDate();
        return !now.isBefore(lastAttempt.plus(delayAfter(publication.getCompletionAttempts())));
    }

    /** The wait before the next attempt, after this many attempts so far. */
    public Duration delayAfter(int attempts) {
        if (attempts <= 1) {
            return firstDelay;
        }
        // Stop doubling well before an overflow; maxDelay caps the result anyway.
        int doublings = Math.min(attempts - 1, 20);
        Duration delay = firstDelay.multipliedBy(1L << doublings);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}
