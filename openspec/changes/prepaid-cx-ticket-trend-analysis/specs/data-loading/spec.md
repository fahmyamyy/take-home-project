## ADDED Requirements

### Requirement: File-based database with no server
The system SHALL load `tickets.csv`, `ticket_notes.csv`, `network_incidents.csv` and `region_base.csv` from the data
folder into a SQLite database file, without requiring any database server installation.

#### Scenario: Fresh load on a clean machine
- **WHEN** the application starts with only a JDK installed and the CSVs present
- **THEN** a SQLite file is created containing all four tables with every CSV row imported

#### Scenario: Open tickets and missing CSAT
- **WHEN** a ticket has an empty `resolution_minutes` or `csat_score`
- **THEN** the value is stored as NULL, not as 0

### Requirement: Reproducible rebuild
The database SHALL be rebuilt from the CSVs on every start so it never reflects stale data.

#### Scenario: Restart after a CSV or label change
- **WHEN** the application is started again
- **THEN** all tables are dropped and recreated from the current CSV and label files

### Requirement: Derived analysis fields
The loader SHALL derive the Monday `week_start` of each ticket, and for each note a normalised `base_text` (with
repeat-contact prefixes removed) plus the number of prefixes removed.

#### Scenario: Nested follow-up prefix
- **WHEN** a note reads "this is a follow up, still not resolved: <complaint>"
- **THEN** `base_text` is "<complaint>" and `followup_depth` is 2

### Requirement: Correct CSV parsing
The loader SHALL parse RFC 4180 CSV, including quoted fields containing commas, quotes and newlines.

#### Scenario: Quoted comma in note text
- **WHEN** a note field is `"paket hilang, pulsa terpotong"`
- **THEN** it is stored as one field with the comma preserved
