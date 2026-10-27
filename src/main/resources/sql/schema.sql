-- Schema for cx.db (SQLite). Created by the `load` command, which also imports
-- the CSVs in data/. Timestamps are stored as ISO-8601 text ('YYYY-MM-DD HH:MM:SS'),
-- which SQLite's date functions understand and which sorts chronologically.

DROP VIEW  IF EXISTS ticket_topics;
DROP TABLE IF EXISTS note_topics;
DROP TABLE IF EXISTS ticket_notes;
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS network_incidents;
DROP TABLE IF EXISTS region_base;

CREATE TABLE tickets (
    ticket_id              TEXT PRIMARY KEY,
    subscriber_id          TEXT    NOT NULL,
    created_at             TEXT    NOT NULL,
    week_start             TEXT    NOT NULL,   -- Monday of created_at's week, derived on load
    channel                TEXT    NOT NULL,
    category               TEXT    NOT NULL,
    subcategory            TEXT    NOT NULL,
    segment                TEXT    NOT NULL,
    region                 TEXT    NOT NULL,
    priority               TEXT    NOT NULL,
    status                 TEXT    NOT NULL,
    first_response_minutes INTEGER NOT NULL,
    resolution_minutes     INTEGER,            -- NULL while still open
    reopened_flag          INTEGER NOT NULL,
    csat_score             INTEGER             -- NULL when no survey response
);

-- note_text is the raw note; base_text has repeat-contact prefixes such as
-- "follow up dari laporan kemarin, " stripped, and followup_depth counts how many
-- were stripped. Both are derived on load by NoteNormalizer.
CREATE TABLE ticket_notes (
    ticket_id      TEXT PRIMARY KEY REFERENCES tickets (ticket_id),
    note_text      TEXT    NOT NULL,
    base_text      TEXT    NOT NULL,
    followup_depth INTEGER NOT NULL
);

CREATE TABLE network_incidents (
    incident_id      TEXT PRIMARY KEY,
    region           TEXT NOT NULL,
    start_time       TEXT NOT NULL,
    end_time         TEXT NOT NULL,
    affected_service TEXT NOT NULL,
    cause            TEXT NOT NULL,
    severity         TEXT NOT NULL
);

CREATE TABLE region_base (
    region          TEXT PRIMARY KEY,
    subscriber_base INTEGER NOT NULL
);

-- Our topic for each distinct base_text (Part 3a), merged on load from the
-- model's cache (output/note_topics_llm.csv) and the human review
-- (review/topic_review.csv). topic_* is the final label: the reviewed one when a
-- review exists, otherwise the model's. llm_* keeps the model's raw answer so it
-- can be measured against the review. Empty until `classify` has run.
CREATE TABLE note_topics (
    base_text         TEXT PRIMARY KEY,
    topic_category    TEXT NOT NULL,
    topic_subcategory TEXT NOT NULL,
    source            TEXT NOT NULL,   -- 'review' or 'llm'
    llm_category      TEXT,
    llm_subcategory   TEXT
);

CREATE INDEX idx_tickets_week       ON tickets (week_start, category, region);
CREATE INDEX idx_tickets_subscriber ON tickets (subscriber_id, created_at);
CREATE INDEX idx_notes_base         ON ticket_notes (base_text);

-- Every ticket with both labels side by side: the agent's and ours.
CREATE VIEW ticket_topics AS
SELECT t.*,
       n.base_text,
       n.followup_depth,
       nt.topic_category,
       nt.topic_subcategory
FROM tickets t
JOIN ticket_notes n USING (ticket_id)
LEFT JOIN note_topics nt USING (base_text);
