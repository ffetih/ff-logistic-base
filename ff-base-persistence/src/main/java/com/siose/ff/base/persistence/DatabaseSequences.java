package com.siose.ff.base.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hands out business numbers from database sequences, so restarts and extra instances never
 * repeat a number. Each product module owns its sequence names and creates them in its migrations.
 */
public final class DatabaseSequences {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSequences(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long next(String sequenceName) {
        if (sequenceName == null || !sequenceName.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Refusing to read a sequence with an unexpected name");
        }
        Long value = jdbcTemplate.queryForObject("select nextval('" + sequenceName + "')", Long.class);
        if (value == null) {
            throw new IllegalStateException("Sequence " + sequenceName + " returned no value");
        }
        return value;
    }
}
