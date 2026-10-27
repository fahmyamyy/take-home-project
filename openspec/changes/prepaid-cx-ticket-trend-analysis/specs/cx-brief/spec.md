## ADDED Requirements

### Requirement: Pipeline-generated one-page brief
The pipeline SHALL generate a one-page brief for a non-technical executive covering the most recent four weeks, and the
generated output SHALL be committed.

#### Scenario: Generate brief
- **WHEN** the `brief` command runs
- **THEN** `output/cx_brief.md` is written without manual editing, along with the facts it was built from

### Requirement: Rising items with numbers
The brief SHALL state what is rising, with concrete numbers (baseline vs recent).

#### Scenario: Rising topic
- **WHEN** a topic is materially above its baseline
- **THEN** the brief gives the baseline, the recent weekly level and the percentage change

### Requirement: Known-cause explanations
The brief SHALL state which movements are most likely explained by something already known, such as network incidents.

#### Scenario: Outage spike
- **WHEN** a spike overlaps a recorded network incident
- **THEN** it is listed as explained, naming the incident

#### Scenario: Incident with no effect
- **WHEN** an incident in the window produced no measurable rise
- **THEN** the brief says so explicitly

### Requirement: Genuine problems flagged for attention
The brief SHALL identify genuine product or process problems needing attention, separately from explained noise and
from small rises being watched.

#### Scenario: Unexplained, material, statistically clear rise
- **WHEN** a topic is materially up, statistically clear, and not explained by an incident
- **THEN** it appears under "needs attention" with a concrete ask

#### Scenario: Chronic process issue
- **WHEN** an SLA breach rate is high but unchanged from the baseline
- **THEN** it is described as chronic, not as a new trend

### Requirement: Generated text is safe
Any LLM-written text in the brief SHALL contain only numbers present in the computed facts; otherwise a deterministic
fallback SHALL be used.

#### Scenario: Hallucinated number
- **WHEN** the model's text contains a number not in the facts
- **THEN** that text is rejected and the fallback is used
