package com.siose.ff.base.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Settings every FF application shares, in {@code META-INF/ff/app-defaults.yml}: the actuator
 * endpoints and health groups the chart's probes and the canary analysis call, virtual threads,
 * Hibernate checking the schema instead of creating it. They come last, so an application's own
 * {@code application.yml}, its environment and its arguments override any of them.
 *
 * <p>With the {@code migrate} profile it also turns the web server off, before Spring Boot decides
 * which kind of application to start, and keeps Kafka listeners from starting.
 *
 * <p>Runs after Spring Boot has read the configuration files, so the active profiles are known.
 */
public class FfDefaults implements EnvironmentPostProcessor, Ordered {

    static final String DEFAULTS = "META-INF/ff/app-defaults.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            List<PropertySource<?>> defaults = new YamlPropertySourceLoader()
                    .load("ffDefaults", new ClassPathResource(DEFAULTS, FfDefaults.class.getClassLoader()));
            defaults.forEach(environment.getPropertySources()::addLast);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DEFAULTS, e);
        }
        if (environment.matchesProfiles(FfApplication.MIGRATE_PROFILE)) {
            // No web server, and no Kafka listener taking messages while the Job only migrates.
            environment.getPropertySources().addFirst(new MapPropertySource("ffMigrate",
                    Map.of("spring.main.web-application-type", "none",
                            "spring.kafka.listener.auto-startup", "false")));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
