## ADDED Requirements

### Requirement: Topic for every note
The system SHALL assign a topic to every free-text note, including notes the agent filed as unclassified.

#### Scenario: All notes covered
- **WHEN** classification has run
- **THEN** every ticket note maps to exactly one topic

### Requirement: Local or no-cost model with stated choice
Classification SHALL use a local model, a free tier or no LLM, and the README SHALL say which was chosen and why.

#### Scenario: Reviewer without Ollama
- **WHEN** Ollama is not running
- **THEN** classification still completes using the committed cached results

### Requirement: Comparison against agent labels
The system SHALL compare each note's topic with the agent-assigned category, report where they disagree, and quantify
the disagreement without assuming the agent label is correct.

#### Scenario: Disagreement report
- **WHEN** classification has run
- **THEN** a report gives the overall disagreement rate, the main disagreement pairs, and the breakdown by dimension (channel, region, priority, segment)

#### Scenario: Agent label is wrong
- **WHEN** the agent label is "Unclassified" but the note describes a concrete problem
- **THEN** the report counts it as a disagreement attributed to the agent label

### Requirement: Classification quality is measured
The model's labels SHALL be checked against an independent human review, and agreement SHALL be reported.

#### Scenario: Model accuracy reported
- **WHEN** the report is generated
- **THEN** it states how many distinct texts the model labelled the same as the review

### Requirement: Answer key not used
`true_labels.csv` SHALL NOT be used for training, prompting or evaluation, and SHALL NOT be committed.

#### Scenario: Repository contents
- **WHEN** the repository is inspected
- **THEN** `true_labels.csv` is git-ignored and is not referenced by any code
