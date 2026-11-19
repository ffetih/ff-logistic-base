package com.siose.ff.base.app;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts an FF application, the same way in both implementations.
 *
 * <p>One image has two jobs. Normally it serves HTTP. With the {@code migrate} profile (the
 * chart's PreSync Job) it starts without a web server ({@link FfDefaults}), lets the database
 * migrations run during startup, and exits: 0 when they worked, an error when they did not.
 *
 * <pre>
 * public static void main(String[] args) {
 *     FfApplication.run(LogisticsApplication.class, args);
 * }
 * </pre>
 */
public final class FfApplication {

    /** The profile of the migration Job. */
    public static final String MIGRATE_PROFILE = "migrate";

    private FfApplication() {
    }

    public static ConfigurableApplicationContext run(Class<?> application, String... args) {
        ConfigurableApplicationContext context = SpringApplication.run(application, args);
        if (context.getEnvironment().matchesProfiles(MIGRATE_PROFILE)) {
            System.exit(SpringApplication.exit(context));
        }
        return context;
    }
}
