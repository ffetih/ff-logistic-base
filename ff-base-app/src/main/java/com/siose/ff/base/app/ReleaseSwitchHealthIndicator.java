package com.siose.ff.base.app;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Says whether this release should take traffic, which is a different question from whether the
 * process is alive or ready. A canary analysis reads it on {@code /actuator/health/release}; the
 * liveness and readiness probes do not include it, so a release that reports DOWN keeps its pods
 * running and Argo Rollouts, not Kubernetes, decides what happens next.
 *
 * <p>For now the answer comes from {@code ff.release.healthy} ({@code FF_RELEASE_HEALTHY}), so a
 * broken release can be shipped on purpose. Real checks (error rate, a dependency the new version
 * needs) can replace it later without touching the deployment.
 *
 * <p>Its contributor name is {@code releaseSwitch} (the bean name without "HealthIndicator"). It may
 * not be {@code release}: Spring Boot refuses a contributor with the same name as a health group,
 * and {@code release} is the group the analysis reads.
 */
public class ReleaseSwitchHealthIndicator implements HealthIndicator {

    private final boolean healthy;

    public ReleaseSwitchHealthIndicator(boolean healthy) {
        this.healthy = healthy;
    }

    @Override
    public Health health() {
        if (healthy) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "ff.release.healthy is false").build();
    }
}
