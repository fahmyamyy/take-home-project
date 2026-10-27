## ADDED Requirements

### Requirement: Weekly volume per 1,000 subscribers
The SQL SHALL return weekly ticket volume by category and region, normalised per 1,000 subscribers in that region.

#### Scenario: Week with no tickets for a slice
- **WHEN** a category has no tickets in a region in a given week
- **THEN** that week appears with a count of 0 rather than being omitted

### Requirement: SLA breach rate by channel and priority
The SQL SHALL return the SLA breach rate by channel and priority, where a breach is a first response above 60
minutes on a High priority ticket.

#### Scenario: Non-High priority
- **WHEN** the priority is Low or Medium
- **THEN** no breach rate is invented (NULL / "no SLA defined"), and the share above 60 minutes is shown only as context

### Requirement: Repeat contact rate
The SQL SHALL return the share of tickets where the same subscriber raises another ticket within 7 days.

#### Scenario: Tickets near the end of the data
- **WHEN** a ticket was created within the final 7 days of the data
- **THEN** it is excluded from the denominator, because its follow-up window is not fully observed

### Requirement: Fastest growing vs large but flat
The SQL SHALL identify the three categories growing fastest week over week and, separately, the categories that are
large but flat, using a growth measure robust to single outage weeks.

#### Scenario: Category with one outage spike
- **WHEN** a category has one extreme week caused by an outage
- **THEN** the growth measure does not rank it as fast-growing on the strength of that week alone

#### Scenario: Fewer than three genuinely growing categories
- **WHEN** fewer than three categories show material growth
- **THEN** the output labels the rest as within noise instead of inventing growth

### Requirement: Runnable and exported
The queries SHALL live in a single SQL file runnable with the `sqlite3` CLI and through the application, and their
results SHALL be exported to CSV in the repository.

#### Scenario: Reviewer runs the SQL directly
- **WHEN** the reviewer runs `sqlite3 -header -column cx.db < src/main/resources/sql/queries.sql`
- **THEN** every query returns results without error
