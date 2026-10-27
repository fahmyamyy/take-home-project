-- Part 1 — Analysis queries (SQLite 3.25+; window functions required).
--
-- Run against cx.db, which `./mvnw spring-boot:run -Dspring-boot.run.arguments=load`
-- builds from data/:
--     sqlite3 -header -column cx.db < sql/queries.sql
-- or through the app, which also writes each result to output/queries/:
--     ./mvnw spring-boot:run -Dspring-boot.run.arguments=queries
--
-- Conventions used throughout:
--   * Weeks start on Monday. tickets.week_start is derived on load as
--     date(created_at, '-6 days', 'weekday 1'). The data covers 26 full weeks,
--     Mon 2026-03-23 .. Sun 2026-09-20, so there are no partial weeks.
--   * Category is the agent-assigned label, as the question asks. Part 3a shows
--     how far that label can be trusted.


-- Q1. Weekly ticket volume by category and region, per 1,000 subscribers.
--
-- Builds the full week x category x region grid first so that a week with zero
-- tickets shows as 0 rather than silently disappearing — a missing row would make
-- a quiet week look like missing data. per_1k_subscribers is small because it is
-- a weekly rate (e.g. 0.02 = 2 tickets per 100,000 subscribers per week).
WITH weeks AS (
    SELECT DISTINCT week_start FROM tickets
),
grid AS (
    SELECT w.week_start, c.category, r.region, r.subscriber_base
    FROM weeks w
    CROSS JOIN (SELECT DISTINCT category FROM tickets) c
    CROSS JOIN region_base r
),
counts AS (
    SELECT week_start, category, region, COUNT(*) AS tickets
    FROM tickets
    GROUP BY week_start, category, region
)
SELECT g.week_start,
       g.category,
       g.region,
       COALESCE(c.tickets, 0)                                          AS tickets,
       g.subscriber_base,
       ROUND(COALESCE(c.tickets, 0) * 1000.0 / g.subscriber_base, 4) AS per_1k_subscribers
FROM grid g
LEFT JOIN counts c USING (week_start, category, region)
ORDER BY g.week_start, g.category, g.region;


-- Q2. SLA breach rate by channel and priority.
--
-- The brief defines a breach only for High priority (first response > 60 min),
-- so breach_rate_pct is NULL for Low/Medium ("no SLA defined") instead of
-- applying a target nobody set. pct_over_60_min is shown for every priority as
-- context only. The first-response clock exists on every ticket, including
-- open ones, so all tickets are in the denominator.
SELECT priority,
       channel,
       COUNT(*)                                                           AS tickets,
       SUM(first_response_minutes > 60)                                   AS over_60_min,
       CASE WHEN priority = 'High'
            THEN ROUND(100.0 * SUM(first_response_minutes > 60) / COUNT(*), 1)
       END                                                                AS breach_rate_pct,
       ROUND(100.0 * SUM(first_response_minutes > 60) / COUNT(*), 1)     AS pct_over_60_min,
       ROUND(AVG(first_response_minutes), 0)                              AS avg_first_response_min
FROM tickets
GROUP BY priority, channel
ORDER BY CASE priority WHEN 'High' THEN 1 WHEN 'Medium' THEN 2 ELSE 3 END,
         breach_rate_pct DESC,
         channel;


-- Q3. Repeat contact rate — share of tickets where the same subscriber raises
-- another ticket within 7 days.
--
-- For each ticket, LEAD() finds that subscriber's next ticket; it counts as a
-- repeat if the next one is created within (0, 7] days. Looking forward rather
-- than backward means the rate answers "how often does a contact fail to settle
-- the issue". Tickets from the final 7 days of data are excluded from the
-- denominator: their follow-ups, if any, fall outside the data (right-censoring),
-- and including them would understate the rate.
WITH ordered AS (
    SELECT ticket_id,
           subscriber_id,
           created_at,
           LEAD(created_at) OVER (PARTITION BY subscriber_id
                                  ORDER BY created_at, ticket_id) AS next_created_at
    FROM tickets
),
observable AS (
    SELECT o.*
    FROM ordered o
    WHERE julianday(o.created_at) <= (SELECT julianday(MAX(created_at)) - 7 FROM tickets)
)
SELECT COUNT(*)                                                   AS eligible_tickets,
       SUM(next_created_at IS NOT NULL
           AND julianday(next_created_at) - julianday(created_at) <= 7) AS followed_within_7d,
       ROUND(100.0 * SUM(next_created_at IS NOT NULL
           AND julianday(next_created_at) - julianday(created_at) <= 7) / COUNT(*), 2)
                                                                  AS repeat_contact_rate_pct
FROM observable;


-- Q3b. Same rate broken down by the first ticket's category and channel, next
-- to the share of notes that the customer explicitly wrote as a follow-up
-- ("follow up dari laporan kemarin, ...", "second time reporting ..."). The two
-- measure different things: Q3 needs a matching subscriber_id within 7 days;
-- the note prefix catches repeats across channels/IDs or beyond 7 days.
WITH ordered AS (
    SELECT t.ticket_id,
           t.category,
           t.channel,
           t.created_at,
           n.followup_depth,
           LEAD(t.created_at) OVER (PARTITION BY t.subscriber_id
                                    ORDER BY t.created_at, t.ticket_id) AS next_created_at
    FROM tickets t
    JOIN ticket_notes n USING (ticket_id)
)
SELECT category,
       COUNT(*)                                                           AS eligible_tickets,
       ROUND(100.0 * SUM(next_created_at IS NOT NULL
           AND julianday(next_created_at) - julianday(created_at) <= 7) / COUNT(*), 2)
                                                                          AS repeat_contact_rate_pct,
       ROUND(100.0 * SUM(followup_depth > 0) / COUNT(*), 2)               AS note_says_followup_pct
FROM ordered
WHERE julianday(created_at) <= (SELECT julianday(MAX(created_at)) - 7 FROM tickets)
GROUP BY category
ORDER BY repeat_contact_rate_pct DESC;


-- Q4. Fastest-growing categories vs large-but-flat ones.
--
-- Growth is the Theil–Sen slope of weekly volume: the median of the slopes
-- between every pair of weeks. Unlike an ordinary least-squares fit it is not
-- dragged by a single outage week — the July fiber cut (one week at 5x normal
-- Network Quality volume) would otherwise make Network Quality look like a
-- growth story. The slope is divided by the category's median weekly volume to
-- get a comparable %/week, so a small category growing quickly can outrank a
-- large one growing slowly.
--
-- last4_vs_prev8 is a second, simpler lens: average weekly volume over the last
-- 4 weeks divided by the 8 weeks before them.
--
-- Labels, in this order:
--   'fastest growing'          top-3 by %/week AND growing >= 1%/week
--   'large but flat'           at or above the median total volume, |growth| < 1%/week
--   'top-3, but within noise'  top-3 by %/week yet < 1%/week — ranked, not rising
-- The 1%/week bar is a judgment call: over 26 weeks it compounds to ~30%, which
-- is roughly where a CX lead would notice. In this data only one category
-- clears it, and saying so is more useful than presenting three "winners".
WITH weekly AS (
    SELECT category,
           week_start,
           CAST((julianday(week_start) - julianday((SELECT MIN(week_start) FROM tickets))) / 7 AS INTEGER) AS wk,
           COUNT(*) AS n
    FROM tickets
    GROUP BY category, week_start
),
pair_slopes AS (
    SELECT a.category, (b.n - a.n) * 1.0 / (b.wk - a.wk) AS slope
    FROM weekly a
    JOIN weekly b ON a.category = b.category AND b.wk > a.wk
),
ranked_slopes AS (
    SELECT category, slope,
           ROW_NUMBER() OVER (PARTITION BY category ORDER BY slope) AS rn,
           COUNT(*)     OVER (PARTITION BY category)                AS cnt
    FROM pair_slopes
),
theil_sen AS (
    SELECT category, AVG(slope) AS slope_per_week   -- median: middle one (or two) slopes
    FROM ranked_slopes
    WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
    GROUP BY category
),
ranked_weeks AS (
    SELECT category, n,
           ROW_NUMBER() OVER (PARTITION BY category ORDER BY n) AS rn,
           COUNT(*)     OVER (PARTITION BY category)            AS cnt
    FROM weekly
),
median_week AS (
    SELECT category, AVG(n) AS median_weekly
    FROM ranked_weeks
    WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
    GROUP BY category
),
summary AS (
    SELECT w.category,
           SUM(w.n)                                                        AS total_tickets,
           m.median_weekly,
           ts.slope_per_week,
           100.0 * ts.slope_per_week / m.median_weekly                     AS growth_pct_per_week,
           AVG(CASE WHEN w.wk >= (SELECT MAX(wk) FROM weekly) - 3 THEN w.n END)
             / AVG(CASE WHEN w.wk BETWEEN (SELECT MAX(wk) FROM weekly) - 11
                                      AND (SELECT MAX(wk) FROM weekly) - 4 THEN w.n END)
                                                                           AS last4_vs_prev8
    FROM weekly w
    JOIN theil_sen ts USING (category)
    JOIN median_week m USING (category)
    GROUP BY w.category
),
labelled AS (
    SELECT s.*,
           RANK() OVER (ORDER BY growth_pct_per_week DESC) AS growth_rank,
           PERCENT_RANK() OVER (ORDER BY total_tickets)    AS volume_pct_rank
    FROM summary s
)
SELECT category,
       total_tickets,
       ROUND(median_weekly, 0)          AS median_weekly,
       ROUND(slope_per_week, 2)         AS slope_tickets_per_week,
       ROUND(growth_pct_per_week, 2)    AS growth_pct_per_week,
       ROUND(last4_vs_prev8, 2)         AS last4_vs_prev8,
       CASE
           WHEN growth_rank <= 3 AND growth_pct_per_week >= 1.0           THEN 'fastest growing'
           WHEN volume_pct_rank >= 0.5 AND ABS(growth_pct_per_week) < 1.0 THEN 'large but flat'
           WHEN growth_rank <= 3                                          THEN 'top-3, but within noise'
           ELSE ''
       END                              AS label
FROM labelled
ORDER BY growth_pct_per_week DESC;


-- Q4b. The same view one level down. Category-level growth hides where it comes
-- from: Billing & Charging's rise is almost entirely one subcategory.
WITH weekly AS (
    SELECT category || ' > ' || subcategory AS slice,
           CAST((julianday(week_start) - julianday((SELECT MIN(week_start) FROM tickets))) / 7 AS INTEGER) AS wk,
           COUNT(*) AS n
    FROM tickets
    GROUP BY slice, week_start
),
pair_slopes AS (
    SELECT a.slice, (b.n - a.n) * 1.0 / (b.wk - a.wk) AS slope
    FROM weekly a
    JOIN weekly b ON a.slice = b.slice AND b.wk > a.wk
),
ranked_slopes AS (
    SELECT slice, slope,
           ROW_NUMBER() OVER (PARTITION BY slice ORDER BY slope) AS rn,
           COUNT(*)     OVER (PARTITION BY slice)                AS cnt
    FROM pair_slopes
),
theil_sen AS (
    SELECT slice, AVG(slope) AS slope_per_week
    FROM ranked_slopes
    WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
    GROUP BY slice
),
ranked_weeks AS (
    SELECT slice, n,
           ROW_NUMBER() OVER (PARTITION BY slice ORDER BY n) AS rn,
           COUNT(*)     OVER (PARTITION BY slice)            AS cnt
    FROM weekly
),
median_week AS (
    SELECT slice, AVG(n) AS median_weekly
    FROM ranked_weeks
    WHERE rn IN ((cnt + 1) / 2, (cnt + 2) / 2)
    GROUP BY slice
)
SELECT ts.slice                                            AS category_subcategory,
       ROUND(m.median_weekly, 0)                           AS median_weekly,
       ROUND(ts.slope_per_week, 2)                         AS slope_tickets_per_week,
       ROUND(100.0 * ts.slope_per_week / m.median_weekly, 2) AS growth_pct_per_week
FROM theil_sen ts
JOIN median_week m USING (slice)
ORDER BY growth_pct_per_week DESC
LIMIT 10;
