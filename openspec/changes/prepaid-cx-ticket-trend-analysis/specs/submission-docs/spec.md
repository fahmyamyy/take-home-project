## ADDED Requirements

### Requirement: Ten-minute README
The README SHALL get a reviewer running in under 10 minutes on a clean machine.

#### Scenario: Clean machine
- **WHEN** a reviewer with only a JDK follows the README
- **THEN** the build, tests and every part run without installing a database server or paid service

### Requirement: Required reflections
The README SHALL state what the author would do differently with more time, what was left out deliberately, and where
the approach would break at 5 million tickets.

#### Scenario: Reviewer looks for limits
- **WHEN** the reviewer reads the README
- **THEN** all three sections are present and concrete

### Requirement: AI assistance disclosed
The README SHALL disclose use of an AI coding assistant, and design decisions SHALL be documented so they can be explained.

#### Scenario: Disclosure present
- **WHEN** the reviewer reads the README
- **THEN** AI use is disclosed and decisions are linked to a planning document

### Requirement: Deliverable repository
The deliverable SHALL be a Git repository containing the code, SQL, README and generated CX brief.

#### Scenario: Repository contents
- **WHEN** the repository is cloned
- **THEN** it contains the source, the SQL (`src/main/resources/sql/`), `README.md` and `output/cx_brief.md`
