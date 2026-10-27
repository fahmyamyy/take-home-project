# Prepaid CX — Ticket Trend Analysis

Six months of prepaid CX tickets (42,751) → SQL analysis, a `/trends` anomaly API,
note classification and a generated executive brief.
Java 25 · Spring Boot · SQLite · optional Ollama.

## Findings

Full brief: [`output/cx_brief.md`](output/cx_brief.md)

- **Escalate:** "package charged but not active" is up **70%** (109 → 186 a week)
  in all six regions, with no incident behind it.
- **Already explained:** the 13 Jul spike is the Sumatera Utara fiber cut. The
  10 Aug login spike was a one-off and is back to normal.
- **Labels are unreliable:** 90% of "Unclassified" tickets are real complaints,
  so official category counts run ~15% low.
- **Chronic, not new:** 99% of high-priority Email tickets miss the 60-minute SLA.

## Run

Needs **JDK 25** only. Maven comes through the wrapper, SQLite is embedded, and
Ollama is optional.

```bash
./mvnw -q package                                           # build + 37 tests
java -jar target/cx-trends-1.0.0.jar queries classify brief # Part 1, 3a, 3b → output/
java -jar target/cx-trends-1.0.0.jar                        # Part 2 API on :8080
```

```bash
curl 'localhost:8080/trends?category=Network%20Quality&region=Sumatera%20Utara&from=2026-07-01&to=2026-07-31'
```

Chart: <http://localhost:8080/>. To run the SQL directly:
`sqlite3 -header -column cx.db < src/main/resources/sql/queries.sql`.

If `java` is not 25, run `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` (macOS)
and use `$JAVA_HOME/bin/java`. Without Ollama, `classify` uses the committed model
output and the brief uses a template bottom line. To regenerate with the models:
`ollama pull qwen2.5:7b && ollama pull qwen2.5:3b` (7B for classification, ~5 GB RAM;
3B for the brief).

## How each part works

| Part | Where | Key choice |
|---|---|---|
| 1 SQL | [`queries.sql`](src/main/resources/sql/queries.sql) → `output/queries/` | Growth = Theil–Sen slope, so one outage week can't fake a trend. Only Billing & Charging grows. |
| 2 API | `trends/` | Flag a week outside `median(prev 8 wks) ± max(3 × 1.4826 × MAD, 25%, 5)`. A median is used, not a mean, so the July outage can't hide the next problem. Each week lists any incident active that week or the week before. |
| 3a Classify | `classify/`, [`report`](output/classification_report.md) | Local `qwen2.5:7b` (notes never leave the laptop) labels the 91 distinct note texts: **88/91** match the human review (3B: 81/91, [all experiments](docs/experiments/classification/README.md)). All 91 are reviewed; ambiguous texts are kept apart and queued for a person (`output/needs_human_check.csv`). 14.5% of tickets disagree with the agent label. |
| 3b Brief | `brief/` | Rules in code decide escalate / explained / watch. The LLM writes only the bottom line, and that line is rejected if it contains a number not in the facts. |

Step by step, what runs and what it produces: [`HOW_IT_WORKS.md`](HOW_IT_WORKS.md).
Every decision, the alternatives and the trade-offs: [`docs/PLANNING.md`](docs/PLANNING.md).

## Limits

**Left out on purpose:**
- `true_labels.csv`: it looks like the answer key, so it is unused and git-ignored.
- Agent-level analysis: the data has no agent ID.
- CSAT modelling: ~70% of tickets have no score.
- Docker, CI.

**With more time:**
- A second reviewer on the labels (Cohen's κ).
- Per-region alerts in the brief.
- Hourly incident attribution.

**At 5M tickets:**
- Reloading CSVs on every start becomes too slow. Load once into a columnar store
  and serve `/trends` from a pre-aggregated weekly table.
- LLM-per-note becomes too slow (~35 CPU-days). Use embeddings plus a classifier
  trained on the reviewed labels, and send only low-confidence notes to the LLM.

## AI assistance

Built with Claude Code as a pair programmer. I made, and can explain, every
decision in [`docs/PLANNING.md`](docs/PLANNING.md).
