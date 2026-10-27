## Context

The assignment (`Prepaid_CX_Ticket_Trend_Assignment.pdf`) provides four synthetic CSVs covering 23 Mar – 20 Sep 2026 and
asks for SQL analysis (30%), a `/trends` API (30%) and an AI part (40%): note classification against agent labels and a
generated executive brief. Hard constraints: everything runs on a laptop, no database server, no cloud deployment, no
paid API. The reviewer must be running in under 10 minutes. Grading emphasises separating known-cause noise from signal
and saying so plainly to a non-technical reader.

The author's main language is Java and the target company mostly uses Java, so the stack is Java rather than the
Python the PDF hints at. A file named `true_labels.csv` ships with the assignment and is probably the grader's answer
key for 3a.

## Goals / Non-Goals

**Goals:**
- One self-contained Java application (single JAR) covering all three parts through subcommands and an HTTP server.
- Reproducible outputs: rebuilding from the CSVs yields byte-identical committed artefacts.
- Every judgment (anomaly rule, growth measure, escalation rule) is explicit, documented and tested.
- Runs without Ollama; the LLM only adds value where it is safe.

**Non-Goals:**
- Authentication, Docker, CI, deployment.
- Agent-level analysis (no agent ID in the data) and CSAT modelling (~70% missing).
- Using `true_labels.csv` for training, prompt tuning or evaluation.

## Decisions

1. **Java 25 + Spring Boot 4.1.2 + Maven wrapper.** One build tool, one runtime, and the reviewer needs only a JDK.
   Alternatives: Python (PDF's suggestion; not the author's language), Go (fine, but Java matches the target team).
2. **SQLite via `sqlite-jdbc`, rebuilt from CSV on every start.** Embedded, no server, and the DB can never go stale
   (~1 s for 42k rows). DuckDB was considered (faster analytics) and rejected by the author in favour of SQLite.
3. **Week = Monday–Sunday, `week_start` derived once on load.** The data covers exactly 26 full weeks, and all parts
   share one definition.
4. **Part 1 interpretation choices** live as SQL comments: the full week × category × region grid (zeros visible); SLA
   breach is defined only for High priority (others NULL, not invented); repeat contact is right-censored (the last
   7 days are excluded from the denominator); growth uses a Theil–Sen slope as % of median weekly volume (robust to the
   July outage, unlike OLS), with "large but flat" as a separate list.
5. **Anomaly rule: median of the previous 8 weeks ± max(3 × 1.4826 × MAD, 25% of median, 5)**, with at least 4 weeks
   of history. Median/MAD resists outage weeks; the floors stop small slices from flagging every wobble. The baseline
   reads 8 weeks before `from`, so narrow ranges are still judged. Incidents active that week or ending the previous
   week are attached, so the API answers "is there already an explanation?".
6. **Classification into the agents' own taxonomy.** This makes the labels directly comparable. Notes are normalised
   (repeat-contact prefixes stripped) to 91 distinct texts, which are classified once by local `qwen2.5:3b` with an
   enum-constrained JSON schema and cached. All 91 are human-reviewed; the reviewed label is final and the model is
   scored against it. Alternatives: TF-IDF + k-means (unlabelled clusters, can't compare to agent categories), a cloud
   API (sends notes off-laptop).
7. **Brief = rules in code, template body, LLM writes only the "Bottom line".** The fact builder applies explicit
   escalate / watch / explained / process rules. The LLM paragraph is accepted only if every number appears in the
   facts, it names every escalated topic, and it stays ≤ 70 words; otherwise a template sentence is used. A full-LLM
   brief was tried, drifted in meaning, and is recorded in `docs/experiments/`.
8. **Generated artefacts are committed** (`output/`), so a reviewer without Ollama sees the real outputs.

## Risks / Trade-offs

- [3B model misclassifies ~10% of texts] → Every text is human-reviewed; the model's raw answer is kept and scored.
- [Human review done by the author could be biased] → Review notes per row; a second reviewer plus Cohen's κ is listed as more-time work.
- [Weekly incident overlap is coarse] → Previous-week linking covers follow-up tails; day-level attribution is listed as more-time work.
- [SQLite rebuild per start doesn't scale] → Documented 5M-ticket plan: offline load into a columnar store, plus a pre-aggregated weekly table.
- [Brief thresholds are tuned on this dataset] → Thresholds are constants, stated in the brief's "About these numbers" section and in the README.

## Migration Plan

Not applicable (greenfield, no deployment).

## Open Questions

- Author must finish checking all 91 reviewed labels before submission.
- Whether to tell the recruiter that `true_labels.csv` was shipped with the assignment.
