# How it works

One Spring Boot JAR covers everything. The argument you pass picks what runs.

```
java -jar target/cx-trends-1.0.0.jar [load] [queries] [classify] [brief]   → batch run, then exit
java -jar target/cx-trends-1.0.0.jar                                       → HTTP server on :8080
```

```
data/*.csv ──► DataLoader ──► cx.db (SQLite)
                                 │
     ┌───────────────┬───────────┼───────────────┬──────────────────┐
     ▼               ▼           ▼               ▼                  ▼
  queries         /trends     classify         brief          sqlite3 CLI
  (Part 1)       (Part 2)     (Part 3a)       (Part 3b)        (optional)
     │               │           │               │
output/queries/   JSON +     note_topics_llm   brief_facts.json
  Q*.csv          chart      classification_   cx_brief.md
                             report.md
```

---

## 0. Build

| Executes | Does | Produces |
|---|---|---|
| `./mvnw -q package` | Downloads Maven 3.9.16 and the dependencies, compiles, runs 37 tests | `target/cx-trends-1.0.0.jar` (the SQL files are packed inside it) |

## 1. Start-up (every run)

| Step | Executes | Does | Produces |
|---|---|---|---|
| 1 | `CxTrendsApplication.main` | Any batch argument → no web server; none → web server | Spring context |
| 2 | `DataLoader.load()` | Runs `sql/schema.sql`: drops and recreates every table | Empty `cx.db` |
| 3 | `loadTickets` | Imports `tickets.csv`; adds `week_start` (the Monday); empty values become NULL | `tickets` (42,751 rows) |
| 4 | `loadNotes` | Imports `ticket_notes.csv`; `NoteNormalizer` strips repeat-contact prefixes such as "follow up dari laporan kemarin," | `ticket_notes` with `base_text` (91 distinct) and `followup_depth` |
| 5 | `loadIncidents`, `loadRegions` | Imports the other two CSVs | `network_incidents`, `region_base` |
| 6 | `reloadNoteTopics` | Merges the model labels (`output/note_topics_llm.csv`) with the human labels (`review/topic_review.csv`); the human label wins | `note_topics`, view `ticket_topics` |
| 7 | `CommandRunner` | Runs each argument in order | see below |

The whole load takes about 1 s. The DB is always rebuilt from the CSVs, so it can never be stale.

## 2. `queries` — Part 1

| Executes | Does | Produces |
|---|---|---|
| `QueriesCommand` → `src/main/resources/sql/queries.sql` | Runs each statement, prints the first 15 rows | `output/queries/Q1.csv` … `Q4b.csv` |

| Query | Answers | How |
|---|---|---|
| Q1 | Weekly volume per 1,000 subscribers, by category × region | Full week × category × region grid, so a zero week shows as 0 |
| Q2 | SLA breach rate by channel × priority | Breach = High priority and first response > 60 min; Low/Medium = NULL (no SLA defined) |
| Q3 | Repeat contact rate | Same subscriber opens another ticket within 7 days; the last 7 days are left out of the denominator. Result: **22.3%** |
| Q3b | Q3 by category and channel | Plus the share of notes written as a follow-up |
| Q4 | Fastest growing vs large but flat | Theil–Sen slope as % of the median week (robust to the outage). Only Billing & Charging grows |
| Q4b | Q4 by subcategory | The growth comes from one subcategory: Package charged but not active |

## 3. Server mode — Part 2

| Executes | Does | Produces |
|---|---|---|
| `GET /trends?category=&region=&from=&to=` → `TrendController` | Validates input; on bad input returns 400 with the valid values | JSON |
| `TrendService` | Matches values case-insensitively; snaps `from`/`to` to whole weeks; also reads the 8 weeks before `from` as history | — |
| `TrendRepository` | Counts tickets per week for the slice; finds incidents | — |
| `AnomalyDetector` | Flags a week outside `median(prev 8) ± max(3 × 1.4826 × MAD, 25%, 5)`; needs at least 4 weeks of history | `anomaly`, `direction`, expected range per week |
| `Incident` | Attaches incidents in the same region active that week (`during`) or ending the week before (`previous_week`) | `knownIncidents` per week |
| `GET /` → `static/index.html` | Chart.js chart of the same endpoint | Chart with the expected band, anomalies in red, and an incident table |

## 4. `classify` — Part 3a

| Step | Executes | Does | Produces |
|---|---|---|---|
| 1 | `ClassifyCommand` | Lists the 91 `base_text`s; skips those already in the cache | Texts still to classify (normally 0) |
| 2 | `OllamaClient.available()` | Only needed if step 1 found new texts; fails with a clear message if Ollama is down | — |
| 3 | `TopicClassifier` → Ollama `qwen2.5:7b` (`cx.ollama.classify-model`) | Per text: prompt with the `Taxonomy` (27 topics, one-line definitions); the JSON schema forces `problem` first, then `topic` from an enum; temperature 0, seed 42; up to 3 tries | One label per text |
| 4 | `TopicFiles` | Saves after every text, so an interrupted run resumes | `output/note_topics_llm.csv` |
| 5 | `DataLoader.reloadNoteTopics` | Model and human labels are merged again; texts listed in `review/ambiguous.csv` are flagged (their reviewed topic must be one of the candidates) | `note_topics` (with `ambiguous`) |
| 6 | `ClassificationReport` | Model vs review (88/91; 86/89 on clear texts); ambiguous texts listed apart; agent label vs our topic (14.5% disagree, all "Unclassified"); breakdown by channel, region, priority and segment; effect on counts | `output/classification_report.md` |
| 7 | `ClassificationReport` | Queues every text a person must look at: ambiguous, model ≠ review, not reviewed yet | `output/needs_human_check.csv` |

`review/topic_review.csv` is the human check of all 91 texts. It is the final label; the model's answer is only used to measure its accuracy. `review/ambiguous.csv` lists the texts that fit more than one topic. Every prompt and model version tried is in `docs/experiments/classification/`.

## 5. `brief` — Part 3b

| Step | Executes | Does | Produces |
|---|---|---|---|
| 1 | `BriefFactsBuilder` | Window: last 4 weeks vs the 8 before, using the corrected topics | — |
| 2 | ↳ needs attention | ≥ 25% up, ≥ 20 extra tickets a week, Poisson z ≥ 4, no incident behind it. Adds regions, segments, resolution, reopen rate, CSAT | `needsAttention` |
| 3 | ↳ watch | ≥ 20% up but small; checks against the busiest earlier week | `watchList` |
| 4 | ↳ explained | Each incident checked for real impact (INC000004: none); earlier spikes with a cause, or "no incident on record" | `explained` |
| 5 | ↳ process | High-priority SLA miss rate per channel vs baseline: new (≥ 10 points up) or chronic | `processIssues` |
| 6 | ↳ data quality | Share of "Unclassified" tickets that are real complaints | `dataQuality` |
| 7 | `BriefCommand` | Writes the facts | `output/brief_facts.json` |
| 8 | `BriefTemplate` | Renders the body from the facts. No LLM | Brief body |
| 9 | `BriefWriter` → Ollama `qwen2.5:3b` (`cx.ollama.brief-model`) | Writes only the 3-sentence bottom line from the facts (topic names without their category) | Bottom-line text |
| 10 | `BriefWriter.check` + `NumberGuard` | Rejects the text if it has a number not in the facts, misses a topic, lacks "Assign a PIC to find the root cause and report back next week.", uses ">" or filler, or is over 70 words. 3 tries, then the template line | Accepted bottom line |
| 11 | `BriefCommand` | Bottom line + body + footer saying which one was used | `output/cx_brief.md` |

Without Ollama, step 9 is skipped and the template bottom line is used.

## 6. Files

| Path | Role | Who writes it |
|---|---|---|
| `data/*.csv` | Input | given |
| `review/topic_review.csv` | Human labels for the 91 texts | author |
| `review/ambiguous.csv` | Texts that fit more than one topic, with the candidates | author |
| `src/main/resources/sql/` | Schema and Part 1 SQL | author |
| `src/main/resources/application.yml` | Paths, DB, Ollama settings | author |
| `cx.db` | SQLite, rebuilt every run (git-ignored) | `DataLoader` |
| `output/queries/Q*.csv` | Part 1 results | `queries` |
| `output/note_topics_llm.csv` | Model labels (cache) | `classify` |
| `output/classification_report.md` | Part 3a report | `classify` |
| `output/needs_human_check.csv` | Queue of texts a person must look at, with the reason | `classify` |
| `output/brief_facts.json` | Every number the brief may use | `brief` |
| `output/cx_brief.md` | The executive brief | `brief` |
| `true_labels.csv` | Probable answer key — never read (git-ignored) | given |

Do not edit `output/` by hand: the next run overwrites it. Change the code that generates it instead.

## 7. Tests (`./mvnw test`)

| Test | Covers |
|---|---|
| `AnomalyDetectorTest` | The flag rule, floors, an earlier spike not masking a later one, minimum history |
| `TrendControllerTest` | `/trends` on real data: the July outage flagged with its incident, validation, case-insensitive matching |
| `NoteNormalizerTest`, `CsvTest` | Prefix stripping; CSV quoting |
| `TopicFilesTest` | Reading `ambiguous.csv`; rejecting a candidate that is not a topic |
| `BriefFactsBuilderTest` | The real data: Package charged but not active escalated, incidents explained, Email SLA chronic |
| `BriefWriterTest` | Bottom-line checks: numbers, topic names, the PIC ask, ">", filler, length |
