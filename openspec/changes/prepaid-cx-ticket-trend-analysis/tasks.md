## 1. Project setup

- [x] 1.1 Create Maven project (Java 25, Spring Boot 4.1.2 parent, Maven wrapper, `sqlite-jdbc`)
- [x] 1.2 Move the provided CSVs into `data/`; git-ignore `true_labels.csv`, the PDF, `cx.db` and `target/`
- [x] 1.3 Add `application.yml` with data/output/review/sql dirs, DB path and Ollama settings
- [x] 1.4 Add CLI dispatch: `queries`, `classify`, `brief` run in batch mode; no argument starts the HTTP server

## 2. Data loading

- [x] 2.1 Write `src/main/resources/sql/schema.sql` (tickets, ticket_notes, network_incidents, region_base, note_topics, view `ticket_topics`, indexes)
- [x] 2.2 Implement an RFC 4180 CSV parser with tests (quoted commas, quotes, newlines)
- [x] 2.3 Implement a note normaliser that strips nested repeat-contact prefixes, with tests
- [x] 2.4 Implement a loader that rebuilds the DB on every start, derives `week_start` and `base_text`, stores empty values as NULL

## 3. Part 1 — SQL analysis

- [x] 3.1 Q1 weekly volume per 1,000 subscribers over the full week × category × region grid
- [x] 3.2 Q2 SLA breach rate by channel × priority (breach only for High; context share for others)
- [x] 3.3 Q3 repeat-contact rate within 7 days, right-censored; Q3b by category plus note follow-up share
- [x] 3.4 Q4 Theil–Sen growth %/week vs size, labelling fastest growing / large but flat / within noise; Q4b by subcategory
- [x] 3.5 `queries` command runs `src/main/resources/sql/queries.sql` and exports each result to `output/queries/Qn.csv`

## 4. Part 2 — /trends API

- [x] 4.1 Implement the anomaly detector (median of 8 prior weeks ± max(3·1.4826·MAD, 25%, 5), ≥4 weeks history) with tests
- [x] 4.2 Implement the repository: data range, categories, regions, weekly counts, incidents
- [x] 4.3 Implement the service: case-insensitive validation, week snapping, range clipping, baseline read before `from`
- [x] 4.4 Attach incidents active during the week or ending the previous week
- [x] 4.5 Implement the controller `GET /trends`, with ProblemDetail 400s for bad dates, `from > to`, unknown values
- [x] 4.6 Endpoint tests (outage week flagged with incident, validation errors)
- [x] 4.7 Static chart page at `/` (Chart.js) showing counts, expected range and flagged weeks

## 5. Part 3a — Note classification

- [x] 5.1 Define the taxonomy (agent categories/subcategories with one-line definitions)
- [x] 5.2 Implement the Ollama client (availability check, chat with JSON schema format)
- [x] 5.3 Implement the classifier: enum-constrained topic, problem restated first, retries; cache to `output/note_topics_llm.csv`
- [x] 5.4 Human-review all distinct texts in `review/topic_review.csv`; reviewed label is final, model label kept
- [x] 5.5 Merge cache and review into `note_topics` on load
- [x] 5.6 Generate `output/classification_report.md`: model vs review agreement, agent vs topic disagreement rate, top pairs, breakdown by dimension

## 6. Part 3b — Weekly CX brief

- [x] 6.1 Fact builder: recent 4 weeks vs previous 8, rising topics with regions, impact and segments
- [x] 6.2 Escalate/watch rules (≥25% up, ≥20 extra/week, Poisson z ≥ 4, not incident-explained; watch ≥20% with earlier-range check)
- [x] 6.3 Explained section: incident impact in both directions, earlier spikes with or without known cause
- [x] 6.4 Process issues: SLA breach compared with baseline (chronic vs new); data-quality note on Unclassified
- [x] 6.5 Template renderer for the brief body
- [x] 6.6 LLM bottom line with number guard, required topic names, ≤70 words, template fallback; tests
- [x] 6.7 `brief` command writes `output/brief_facts.json` and `output/cx_brief.md`; commit the outputs

## 7. Documentation and delivery

- [x] 7.1 README: findings, ≤10-minute run steps, API examples, part-by-part decisions
- [x] 7.2 README: more time, deliberately left out, 5M-ticket limits, AI-assistant disclosure
- [x] 7.3 `docs/PLANNING.md` with decisions, alternatives and trade-offs; `docs/experiments/` for the rejected full-LLM brief
- [x] 7.4 Verify from a clean copy: build plus tests pass and regenerated outputs match the committed ones
- [x] 7.5 Publish as a private Git repository
