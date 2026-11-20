-- Spring Modulith's event publication registry (JDBC, schema v2): the outbox of the process that
-- runs this script. Unqualified on purpose: AreaMigrations runs it in the process's own schema
-- (ff.events.schema), so every process has an outbox of its own (decision 12).
-- Portable types, so the tests' H2 runs the same script as PostgreSQL. Spring Modulith's own
-- PostgreSQL script also has a hash index on serialized_event, which H2 cannot build. It speeds up
-- marking a publication completed in a large table; the upkeep (OutboxUpkeep) deletes completed
-- rows after a week, which keeps this one small.
CREATE TABLE event_publication (
    id                     UUID                     NOT NULL,
    listener_id            VARCHAR(512)             NOT NULL,
    event_type             VARCHAR(512)             NOT NULL,
    serialized_event       VARCHAR(100000)          NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 VARCHAR(20),
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
