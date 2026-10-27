## Why

A prepaid CX team has six months of tickets (23 Mar – 20 Sep 2026, ~42,000 rows) and no repeatable way to tell
noise that already has a known cause (network outages, one-off product incidents) from signal that needs escalating.
The take-home assignment asks for a small, finished, honest pipeline that runs entirely on a laptop and explains its
findings to an executive who will never read the code.

## What Changes

- Load the four provided CSVs (`tickets`, `ticket_notes`, `network_incidents`, `region_base`) into a file-based
  SQLite database with no server, rebuilt reproducibly from the CSVs.
- Add SQL answering the four Part 1 questions: weekly volume per 1,000 subscribers by category and region, SLA breach
  rate by channel and priority, repeat-contact rate within 7 days, and fastest-growing vs large-but-flat categories.
- Add an HTTP API with one endpoint, `GET /trends?category=&region=&from=&to=`, returning weekly volumes and an
  anomaly flag under a documented rule, plus an optional chart view.
- Classify every free-text note into a topic, compare it with the agent-assigned category, and quantify where they
  disagree without assuming the agent is right.
- Generate a one-page weekly CX brief for the most recent four weeks (rising / explained by something known / genuine
  problem needing attention) from the pipeline, and commit the generated output.
- Write a README that gets a reviewer running in under 10 minutes on a clean machine, and states the model choice,
  more-time items, deliberate omissions, 5-million-ticket limits and AI-assistant use.

## Capabilities

### New Capabilities
- `data-loading`: Import the four CSVs into a reproducible SQLite file with derived fields (week start, normalised note text).
- `sql-analysis`: The four Part 1 SQL answers, runnable as a script with results exported.
- `trends-api`: `GET /trends` with filters, validation, weekly volumes, anomaly flag and rule explanation; optional chart.
- `note-classification`: Topic assignment for every note, agent-vs-topic comparison and a disagreement report.
- `cx-brief`: Pipeline-generated one-page executive brief separating known-cause noise from signal.
- `submission-docs`: README, run instructions, model choice, trade-offs, scaling limits and AI disclosure.

### Modified Capabilities
<!-- None: greenfield project, no existing specs. -->

## Impact

- New Java 25 / Spring Boot 4.1.2 application built with the Maven wrapper; SQLite via `sqlite-jdbc` (embedded, no server).
- Optional local dependency: Ollama with `qwen2.5:3b`. Everything must still run without it.
- New files under `src/` (SQL in `src/main/resources/sql/`), `review/`, `output/` (generated, committed) and `docs/`.
- `true_labels.csv`, shipped alongside the assignment, is treated as a possible answer key: it is never read and is git-ignored.
