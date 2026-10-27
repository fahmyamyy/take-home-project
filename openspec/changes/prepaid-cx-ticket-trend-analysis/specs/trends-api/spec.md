## ADDED Requirements

### Requirement: Single trends endpoint
The system SHALL expose `GET /trends?category=&region=&from=&to=` returning weekly ticket volumes for the filtered
slice. All parameters SHALL be optional.

#### Scenario: Filtered slice
- **WHEN** the client calls `/trends?category=Network Quality&region=Sumatera Utara&from=2026-07-01&to=2026-07-31`
- **THEN** the response lists each week in the range with its ticket count for that category and region

#### Scenario: No filters
- **WHEN** the client calls `/trends` with no parameters
- **THEN** the response covers all categories, all regions and the full data range

### Requirement: Anomaly flag with a documented rule
Each week SHALL carry an anomaly flag for departure from the recent trend under a rule the author defines and explains;
the response SHALL state the rule.

#### Scenario: Outage week
- **WHEN** the week of 13 Jul 2026 is requested for Network Quality in Sumatera Utara
- **THEN** that week is flagged as anomalous (up)

#### Scenario: A past spike does not mask later anomalies
- **WHEN** a single extreme week lies inside the baseline window
- **THEN** it does not widen the expected range enough to hide a later genuine anomaly

#### Scenario: Insufficient history
- **WHEN** a week has fewer than the minimum number of prior weeks
- **THEN** it is not flagged and has no baseline

### Requirement: Known-cause context
The response SHALL attach network incidents relevant to each week, so a flagged week can be read as explained or unexplained.

#### Scenario: Follow-up tail after an outage
- **WHEN** a flagged week follows a week with an incident in the same region
- **THEN** the incident is attached with a relation indicating that it ended in the previous week

### Requirement: Input validation
Invalid input SHALL return HTTP 400 with an explanatory problem detail.

#### Scenario: Unknown category
- **WHEN** the category is not one of the known values
- **THEN** the response is 400 and lists the valid values

#### Scenario: Reversed dates
- **WHEN** `from` is after `to`
- **THEN** the response is 400

### Requirement: Tests and optional chart
The API SHALL have at least two automated tests, and SHALL serve a chart view of the same endpoint.

#### Scenario: Test suite
- **WHEN** the build runs
- **THEN** tests covering the anomaly rule and the endpoint pass

#### Scenario: Chart view
- **WHEN** the reviewer opens `/` in a browser
- **THEN** a chart of weekly volume with the expected range and flagged weeks is shown
