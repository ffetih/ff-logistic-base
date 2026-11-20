package com.siose.ff.base.persistence;

import java.util.regex.Pattern;

/**
 * A business area that owns tables and their migrations: warehousing, transportation, routing.
 *
 * <p>Each area keeps its Flyway scripts in {@code classpath:db/migration/<name>} and records
 * them in its own history table, {@code <name>_flyway_history}. So the same scripts can be run by
 * a process that holds one area (a service) or all of them (the modulith), against the same
 * database, and each process sees exactly its areas' history. An area is declared as a bean;
 * {@link AreaMigrations} migrates every declared area when the application starts.
 *
 * <p>An area may keep its tables in a schema of its own instead of the default one: then Flyway
 * creates that schema if it is missing, runs the scripts in it (unqualified names land there) and
 * keeps the history table there too. Used for what belongs to one process rather than to an area
 * shared by both implementations, such as its outbox ({@code ff-base-events}).
 *
 * @param name   lower-case letters only; it names the script location and the history table
 * @param schema lower-case letters only, or {@code null} for the connection's default schema
 */
public record MigrationArea(String name, String schema) {

    private static final Pattern NAME = Pattern.compile("[a-z]{2,30}");

    public MigrationArea {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("An area name is 2 to 30 lower-case letters: " + name);
        }
        if (schema != null && !NAME.matcher(schema).matches()) {
            throw new IllegalArgumentException("A schema name is 2 to 30 lower-case letters: " + schema);
        }
    }

    /** An area in the default schema, shared by both implementations. */
    public static MigrationArea of(String name) {
        return new MigrationArea(name, null);
    }

    /** An area in a schema of its own. */
    public static MigrationArea of(String name, String schema) {
        return new MigrationArea(name, schema);
    }

    /** Where the area's scripts are. */
    public String location() {
        return "classpath:db/migration/" + name;
    }

    /** The table that records which of the area's scripts have run, in the area's schema. */
    public String historyTable() {
        return name + "_flyway_history";
    }
}
