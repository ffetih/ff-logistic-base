package com.siose.ff.base.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.EventPublication;

class EventRetryPolicyTest {

    private final EventRetryPolicy policy = new EventRetryPolicy(5, Duration.ofMinutes(1), Duration.ofMinutes(10));

    private static EventPublication publication(int attempts, Instant lastAttempt) {
        EventPublication publication = mock(EventPublication.class);
        when(publication.getCompletionAttempts()).thenReturn(attempts);
        when(publication.getLastResubmissionDate()).thenReturn(lastAttempt);
        when(publication.getPublicationDate()).thenReturn(lastAttempt);
        return publication;
    }

    @Test
    void theWaitDoublesUpToTheMaximum() {
        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(policy.delayAfter(4)).isEqualTo(Duration.ofMinutes(8));
        assertThat(policy.delayAfter(5)).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.delayAfter(60)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void dueOnlyAfterTheWait() {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");

        assertThat(policy.isDue(publication(2, now.minusSeconds(119)), now)).isFalse();
        assertThat(policy.isDue(publication(2, now.minusSeconds(120)), now)).isTrue();
    }

    @Test
    void neverAgainAfterTheMaximumAttempts() {
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        EventPublication tired = publication(5, now.minus(Duration.ofDays(1)));

        assertThat(policy.isExhausted(tired)).isTrue();
        assertThat(policy.isDue(tired, now)).isFalse();
    }
}
