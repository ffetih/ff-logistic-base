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
 * @param name lower-case letters only; it names the script location and the history table
 */
public record MigrationArea(String name) {

    private static final Pattern NAME = Pattern.compile("[a-z]{2,30}");

    public MigrationArea {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("An area name is 2 to 30 lower-case letters: " + name);
        }
    }

    public static MigrationArea of(String name) {
        return new MigrationArea(name);
    }

    /** Where the area's scripts are. */
    public String location() {
        return "classpath:db/migration/" + name;
    }

    /** The table that records which of the area's scripts have run. */
    public String historyTable() {
        return name + "_flyway_history";
    }
}
