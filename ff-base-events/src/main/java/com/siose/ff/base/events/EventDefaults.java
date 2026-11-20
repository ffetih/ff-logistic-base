package com.siose.ff.base.events;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Settings that follow from {@code ff.events.schema}, so a process names its outbox once:
 *
 * <ul>
 *   <li>the registry's tables are in that schema ({@code spring.modulith.events.jdbc.schema}),</li>
 *   <li>Flyway creates them, not the registry at startup (the {@code outbox} area),</li>
 *   <li>no pod publishes all unfinished events again when it starts: with four pods that would be
 *       four deliveries; {@link OutboxUpkeep} does it, from one pod at a time,</li>
 *   <li>a row a stopped pod left behind counts as failed after five minutes.</li>
 * </ul>
 *
 * They come last: the application's own configuration overrides any of them.
 */
public class EventDefaults implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String schema = environment.getProperty("ff.events.schema");
        if (schema == null || schema.isBlank()) {
            return;
        }
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.modulith.events.jdbc.schema", schema);
        defaults.put("spring.modulith.events.jdbc.schema-initialization.enabled", "false");
        defaults.put("spring.modulith.events.republish-outstanding-events-on-restart", "false");
        for (String state : new String[] {"published", "processing", "resubmitted"}) {
            defaults.put("spring.modulith.events.staleness." + state, "PT5M");
        }
        environment.getPropertySources().addLast(new MapPropertySource("ffEventDefaults", defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
