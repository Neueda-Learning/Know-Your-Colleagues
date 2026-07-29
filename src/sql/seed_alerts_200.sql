-- MySQL 8.x: generate up to 200 rule-linked alerts for local/demo use.
-- Prerequisites:
--   1. Run src/sql/schema.sql.
--   2. Run src/sql/seed_transactions_100.sql immediately before this script.
--
-- Unlike a rule/transaction cross join, every candidate in this script must
-- satisfy the same core predicate used by its backend rule strategy:
--   * AMOUNT_THRESHOLD: matching currency and amount above the threshold;
--   * VELOCITY: exactly transaction_count previous evaluated transactions in
--     the configured account/time window;
--   * NEW_PAYEE: no earlier evaluated account/payee combination;
--   * DAILY_LIMIT: the current debit is the first transaction that moves the
--     daily evaluated total above the configured limit.
--
-- The script uses the first enabled rule of each type. If a rule type is not
-- available, it creates one default enabled rule. With the companion default
-- transaction dataset, the expected result is 200 alerts: 90 amount, 90 new
-- payee, 10 velocity, and 10 daily-limit alerts.

USE transaction_monitoring;

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';

SET @alert_data_now = UTC_TIMESTAMP(3);

-- Create a default only when no enabled rule of the corresponding type exists.
INSERT INTO rules (
    name,
    description,
    type,
    severity,
    enabled,
    currency,
    threshold_amount,
    transaction_count,
    time_window_minutes,
    daily_limit_amount,
    created_at,
    updated_at,
    version
)
SELECT
    'High-value transaction',
    'Single transaction amount exceeds the configured threshold',
    'AMOUNT_THRESHOLD',
    'HIGH',
    TRUE,
    'USD',
    10000.0000,
    NULL,
    NULL,
    NULL,
    @alert_data_now,
    @alert_data_now,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM rules
    WHERE enabled = TRUE
      AND type = 'AMOUNT_THRESHOLD'
);

INSERT INTO rules (
    name, description, type, severity, enabled, currency,
    threshold_amount, transaction_count, time_window_minutes,
    daily_limit_amount, created_at, updated_at, version
)
SELECT
    'Rapid transaction velocity',
    'Account transaction frequency exceeds the configured window limit',
    'VELOCITY',
    'MEDIUM',
    TRUE,
    NULL,
    NULL,
    5,
    10,
    NULL,
    @alert_data_now,
    @alert_data_now,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM rules
    WHERE enabled = TRUE
      AND type = 'VELOCITY'
);

INSERT INTO rules (
    name, description, type, severity, enabled, currency,
    threshold_amount, transaction_count, time_window_minutes,
    daily_limit_amount, created_at, updated_at, version
)
SELECT
    'New payee detected',
    'Account sends funds to a previously unseen payee',
    'NEW_PAYEE',
    'LOW',
    TRUE,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    @alert_data_now,
    @alert_data_now,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM rules
    WHERE enabled = TRUE
      AND type = 'NEW_PAYEE'
);

INSERT INTO rules (
    name, description, type, severity, enabled, currency,
    threshold_amount, transaction_count, time_window_minutes,
    daily_limit_amount, created_at, updated_at, version
)
SELECT
    'Daily transaction limit',
    'Daily debit total exceeds the configured account limit',
    'DAILY_LIMIT',
    'HIGH',
    TRUE,
    'USD',
    NULL,
    NULL,
    NULL,
    50000.0000,
    @alert_data_now,
    @alert_data_now,
    0
WHERE NOT EXISTS (
    SELECT 1
    FROM rules
    WHERE enabled = TRUE
      AND type = 'DAILY_LIMIT'
);

-- Find the most recent companion transaction run. Its reference format is:
-- TXN-<14-digit UTC timestamp>-<8 hex characters>-<3-digit sequence>.
SET @transaction_run_prefix = (
    SELECT SUBSTRING_INDEX(transaction_ref, '-', 3)
    FROM transactions
    WHERE transaction_ref REGEXP
        '^TXN-[0-9]{14}-[A-F0-9]{8}-[0-9]{3}$'
    ORDER BY id DESC
    LIMIT 1
);

DROP TEMPORARY TABLE IF EXISTS generated_alert_candidates;

CREATE TEMPORARY TABLE generated_alert_candidates AS
WITH ranked_rules AS (
    SELECT
        configured_rule.*,
        ROW_NUMBER() OVER (
            PARTITION BY configured_rule.type
            ORDER BY configured_rule.id
        ) AS type_rank
    FROM rules AS configured_rule
    WHERE configured_rule.enabled = TRUE
), selected_rules AS (
    SELECT *
    FROM ranked_rules
    WHERE type_rank = 1
), matched_candidates AS (
    -- Amount Threshold: same predicate as AmountThresholdRuleStrategy.
    SELECT
        selected_rule.id AS rule_id,
        selected_rule.name AS rule_name,
        selected_rule.type AS rule_type,
        selected_rule.severity,
        selected_rule.currency AS rule_currency,
        selected_rule.threshold_amount,
        selected_rule.transaction_count,
        selected_rule.time_window_minutes,
        selected_rule.daily_limit_amount,
        transaction_source.id AS transaction_id,
        transaction_source.account_id,
        transaction_source.payee_id,
        transaction_source.amount,
        transaction_source.currency,
        transaction_source.transaction_time
    FROM transactions AS transaction_source
    JOIN selected_rules AS selected_rule
      ON selected_rule.type = 'AMOUNT_THRESHOLD'
    WHERE transaction_source.transaction_ref LIKE CONCAT(
        @transaction_run_prefix,
        '-%'
    )
      AND transaction_source.status IN ('NORMAL', 'ABNORMAL')
      AND (
          selected_rule.currency IS NULL
          OR UPPER(selected_rule.currency) = UPPER(transaction_source.currency)
      )
      AND transaction_source.amount > selected_rule.threshold_amount

    UNION ALL

    -- Velocity: the backend adds the current PENDING transaction to exactly
    -- transaction_count earlier evaluated transactions in the time window.
    SELECT
        selected_rule.id,
        selected_rule.name,
        selected_rule.type,
        selected_rule.severity,
        selected_rule.currency,
        selected_rule.threshold_amount,
        selected_rule.transaction_count,
        selected_rule.time_window_minutes,
        selected_rule.daily_limit_amount,
        transaction_source.id,
        transaction_source.account_id,
        transaction_source.payee_id,
        transaction_source.amount,
        transaction_source.currency,
        transaction_source.transaction_time
    FROM transactions AS transaction_source
    JOIN selected_rules AS selected_rule
      ON selected_rule.type = 'VELOCITY'
    WHERE transaction_source.transaction_ref LIKE CONCAT(
        @transaction_run_prefix,
        '-%'
    )
      AND transaction_source.status IN ('NORMAL', 'ABNORMAL')
      AND (
          SELECT COUNT(*)
          FROM transactions AS previous_transaction
          WHERE previous_transaction.account_id = transaction_source.account_id
            AND previous_transaction.status IN ('NORMAL', 'ABNORMAL')
            AND previous_transaction.transaction_time >= DATE_SUB(
                transaction_source.transaction_time,
                INTERVAL selected_rule.time_window_minutes MINUTE
            )
            AND (
                previous_transaction.transaction_time
                    < transaction_source.transaction_time
                OR (
                    previous_transaction.transaction_time
                        = transaction_source.transaction_time
                    AND previous_transaction.id < transaction_source.id
                )
            )
      ) = selected_rule.transaction_count

    UNION ALL

    -- New Payee: no earlier evaluated transaction for this account/payee pair.
    SELECT
        selected_rule.id,
        selected_rule.name,
        selected_rule.type,
        selected_rule.severity,
        selected_rule.currency,
        selected_rule.threshold_amount,
        selected_rule.transaction_count,
        selected_rule.time_window_minutes,
        selected_rule.daily_limit_amount,
        transaction_source.id,
        transaction_source.account_id,
        transaction_source.payee_id,
        transaction_source.amount,
        transaction_source.currency,
        transaction_source.transaction_time
    FROM transactions AS transaction_source
    JOIN selected_rules AS selected_rule
      ON selected_rule.type = 'NEW_PAYEE'
    WHERE transaction_source.transaction_ref LIKE CONCAT(
        @transaction_run_prefix,
        '-%'
    )
      AND transaction_source.status IN ('NORMAL', 'ABNORMAL')
      AND NOT EXISTS (
          SELECT 1
          FROM transactions AS previous_transaction
          WHERE previous_transaction.account_id = transaction_source.account_id
            AND previous_transaction.payee_id = transaction_source.payee_id
            AND previous_transaction.status IN ('NORMAL', 'ABNORMAL')
            AND (
                previous_transaction.transaction_time
                    < transaction_source.transaction_time
                OR (
                    previous_transaction.transaction_time
                        = transaction_source.transaction_time
                    AND previous_transaction.id < transaction_source.id
                )
            )
      )

    UNION ALL

    -- Daily Limit: previous total is at or below the limit, while adding the
    -- current debit moves the total above it for the first time.
    SELECT
        selected_rule.id,
        selected_rule.name,
        selected_rule.type,
        selected_rule.severity,
        selected_rule.currency,
        selected_rule.threshold_amount,
        selected_rule.transaction_count,
        selected_rule.time_window_minutes,
        selected_rule.daily_limit_amount,
        transaction_source.id,
        transaction_source.account_id,
        transaction_source.payee_id,
        transaction_source.amount,
        transaction_source.currency,
        transaction_source.transaction_time
    FROM transactions AS transaction_source
    JOIN selected_rules AS selected_rule
      ON selected_rule.type = 'DAILY_LIMIT'
    WHERE transaction_source.transaction_ref LIKE CONCAT(
        @transaction_run_prefix,
        '-%'
    )
      AND transaction_source.status IN ('NORMAL', 'ABNORMAL')
      AND transaction_source.transaction_type = 'DEBIT'
      AND (
          selected_rule.currency IS NULL
          OR UPPER(selected_rule.currency) = UPPER(transaction_source.currency)
      )
      AND COALESCE((
          SELECT SUM(previous_transaction.amount)
          FROM transactions AS previous_transaction
          WHERE previous_transaction.account_id = transaction_source.account_id
            AND previous_transaction.currency = transaction_source.currency
            AND previous_transaction.transaction_type = 'DEBIT'
            AND previous_transaction.status IN ('NORMAL', 'ABNORMAL')
            AND DATE(previous_transaction.transaction_time)
                = DATE(transaction_source.transaction_time)
            AND (
                previous_transaction.transaction_time
                    < transaction_source.transaction_time
                OR (
                    previous_transaction.transaction_time
                        = transaction_source.transaction_time
                    AND previous_transaction.id < transaction_source.id
                )
            )
      ), 0) <= selected_rule.daily_limit_amount
      AND COALESCE((
          SELECT SUM(previous_transaction.amount)
          FROM transactions AS previous_transaction
          WHERE previous_transaction.account_id = transaction_source.account_id
            AND previous_transaction.currency = transaction_source.currency
            AND previous_transaction.transaction_type = 'DEBIT'
            AND previous_transaction.status IN ('NORMAL', 'ABNORMAL')
            AND DATE(previous_transaction.transaction_time)
                = DATE(transaction_source.transaction_time)
            AND (
                previous_transaction.transaction_time
                    < transaction_source.transaction_time
                OR (
                    previous_transaction.transaction_time
                        = transaction_source.transaction_time
                    AND previous_transaction.id < transaction_source.id
                )
            )
      ), 0) + transaction_source.amount
          > selected_rule.daily_limit_amount
), unused_candidates AS (
    SELECT matched_candidate.*
    FROM matched_candidates AS matched_candidate
    WHERE NOT EXISTS (
        SELECT 1
        FROM alerts AS existing_alert
        WHERE existing_alert.rule_id = matched_candidate.rule_id
          AND existing_alert.trigger_transaction_id
              = matched_candidate.transaction_id
    )
)
SELECT
    ROW_NUMBER() OVER (
        ORDER BY
            CASE unused_candidate.rule_type
                WHEN 'AMOUNT_THRESHOLD' THEN 1
                WHEN 'VELOCITY' THEN 2
                WHEN 'NEW_PAYEE' THEN 3
                ELSE 4
            END,
            unused_candidate.transaction_time,
            unused_candidate.transaction_id
    ) AS sequence_no,
    unused_candidate.*
FROM unused_candidates AS unused_candidate
ORDER BY
    CASE unused_candidate.rule_type
        WHEN 'AMOUNT_THRESHOLD' THEN 1
        WHEN 'VELOCITY' THEN 2
        WHEN 'NEW_PAYEE' THEN 3
        ELSE 4
    END,
    unused_candidate.transaction_time,
    unused_candidate.transaction_id
LIMIT 200;

-- Apply the same final transaction status produced by a FLAGGED evaluation
-- result. Transactions without a candidate remain NORMAL or PENDING.
UPDATE transactions AS transaction_to_update
JOIN (
    SELECT DISTINCT transaction_id
    FROM generated_alert_candidates
) AS flagged_transaction
  ON flagged_transaction.transaction_id = transaction_to_update.id
SET transaction_to_update.status = 'ABNORMAL',
    transaction_to_update.updated_at = @alert_data_now
WHERE transaction_to_update.status = 'NORMAL';

INSERT INTO alerts (
    rule_id,
    trigger_transaction_id,
    account_id,
    rule_name,
    severity,
    status,
    title,
    description,
    resolution_notes,
    created_at,
    acknowledged_at,
    investigating_at,
    closed_at,
    dismissed_at,
    updated_at,
    version
)
SELECT
    candidate.rule_id,
    candidate.transaction_id,
    candidate.account_id,
    candidate.rule_name,
    candidate.severity,
    CASE MOD(candidate.sequence_no - 1, 5)
        WHEN 0 THEN 'OPEN'
        WHEN 1 THEN 'ACKNOWLEDGED'
        WHEN 2 THEN 'INVESTIGATING'
        WHEN 3 THEN 'CLOSED'
        ELSE 'DISMISSED'
    END AS status,
    CASE candidate.rule_type
        WHEN 'AMOUNT_THRESHOLD' THEN 'High-value transaction detected'
        WHEN 'VELOCITY' THEN 'Rapid transaction activity detected'
        WHEN 'NEW_PAYEE' THEN 'Transaction to a new payee detected'
        ELSE 'Daily transaction limit exceeded'
    END AS title,
    CASE candidate.rule_type
        WHEN 'AMOUNT_THRESHOLD' THEN CONCAT(
            'Transaction ',
            candidate.transaction_id,
            ' amount ',
            candidate.amount,
            ' ',
            candidate.currency,
            ' exceeded threshold ',
            candidate.threshold_amount,
            '.'
        )
        WHEN 'VELOCITY' THEN CONCAT(
            candidate.transaction_count + 1,
            ' evaluated transactions occurred within ',
            candidate.time_window_minutes,
            ' minutes for account ',
            candidate.account_id,
            '.'
        )
        WHEN 'NEW_PAYEE' THEN CONCAT(
            'Account ',
            candidate.account_id,
            ' made its first evaluated transaction to payee ',
            candidate.payee_id,
            '.'
        )
        ELSE CONCAT(
            'Daily debit total crossed ',
            candidate.daily_limit_amount,
            ' ',
            candidate.currency,
            ' at transaction ',
            candidate.transaction_id,
            '.'
        )
    END AS description,
    CASE MOD(candidate.sequence_no - 1, 5)
        WHEN 3 THEN 'Reviewed and confirmed as legitimate activity.'
        WHEN 4 THEN 'Dismissed after review as a false positive.'
        ELSE NULL
    END AS resolution_notes,
    DATE_ADD(candidate.transaction_time, INTERVAL 1 SECOND) AS created_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) IN (1, 2, 3, 4)
            THEN DATE_ADD(candidate.transaction_time, INTERVAL 5 MINUTE)
        ELSE NULL
    END AS acknowledged_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) IN (2, 3)
            THEN DATE_ADD(candidate.transaction_time, INTERVAL 15 MINUTE)
        ELSE NULL
    END AS investigating_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) = 3
            THEN DATE_ADD(candidate.transaction_time, INTERVAL 45 MINUTE)
        ELSE NULL
    END AS closed_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) = 4
            THEN DATE_ADD(candidate.transaction_time, INTERVAL 30 MINUTE)
        ELSE NULL
    END AS dismissed_at,
    CASE MOD(candidate.sequence_no - 1, 5)
        WHEN 0 THEN DATE_ADD(candidate.transaction_time, INTERVAL 1 SECOND)
        WHEN 1 THEN DATE_ADD(candidate.transaction_time, INTERVAL 5 MINUTE)
        WHEN 2 THEN DATE_ADD(candidate.transaction_time, INTERVAL 15 MINUTE)
        WHEN 3 THEN DATE_ADD(candidate.transaction_time, INTERVAL 45 MINUTE)
        ELSE DATE_ADD(candidate.transaction_time, INTERVAL 30 MINUTE)
    END AS updated_at,
    0 AS version
FROM generated_alert_candidates AS candidate;

-- Trigger-only rules link one transaction. Aggregate rules link every evaluated
-- transaction that participated in the matching velocity/daily-limit result.
INSERT IGNORE INTO alert_transactions (alert_id, transaction_id)
SELECT DISTINCT
    generated_alert.id,
    related_transaction.id
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
JOIN transactions AS related_transaction
  ON related_transaction.id = candidate.transaction_id
  OR (
      candidate.rule_type = 'VELOCITY'
      AND related_transaction.account_id = candidate.account_id
      AND related_transaction.status IN ('NORMAL', 'ABNORMAL')
      AND related_transaction.transaction_time >= DATE_SUB(
          candidate.transaction_time,
          INTERVAL candidate.time_window_minutes MINUTE
      )
      AND (
          related_transaction.transaction_time < candidate.transaction_time
          OR (
              related_transaction.transaction_time = candidate.transaction_time
              AND related_transaction.id <= candidate.transaction_id
          )
      )
  )
  OR (
      candidate.rule_type = 'DAILY_LIMIT'
      AND related_transaction.account_id = candidate.account_id
      AND related_transaction.currency = candidate.currency
      AND related_transaction.transaction_type = 'DEBIT'
      AND related_transaction.status IN ('NORMAL', 'ABNORMAL')
      AND DATE(related_transaction.transaction_time)
          = DATE(candidate.transaction_time)
      AND (
          related_transaction.transaction_time < candidate.transaction_time
          OR (
              related_transaction.transaction_time = candidate.transaction_time
              AND related_transaction.id <= candidate.transaction_id
          )
      )
  );

-- Initial lifecycle entry: NULL -> OPEN.
INSERT INTO alert_history (
    alert_id, from_status, to_status, notes, changed_at
)
SELECT
    generated_alert.id,
    NULL,
    'OPEN',
    'Alert opened after automated rule evaluation.',
    generated_alert.created_at
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id;

-- OPEN -> ACKNOWLEDGED for every alert no longer in OPEN.
INSERT INTO alert_history (
    alert_id, from_status, to_status, notes, changed_at
)
SELECT
    generated_alert.id,
    'OPEN',
    'ACKNOWLEDGED',
    'Acknowledged by demo operator.',
    generated_alert.acknowledged_at
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE generated_alert.status IN (
    'ACKNOWLEDGED',
    'INVESTIGATING',
    'CLOSED',
    'DISMISSED'
);

-- ACKNOWLEDGED -> INVESTIGATING.
INSERT INTO alert_history (
    alert_id, from_status, to_status, notes, changed_at
)
SELECT
    generated_alert.id,
    'ACKNOWLEDGED',
    'INVESTIGATING',
    'Investigation started by demo operator.',
    generated_alert.investigating_at
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE generated_alert.status IN ('INVESTIGATING', 'CLOSED');

-- INVESTIGATING -> CLOSED.
INSERT INTO alert_history (
    alert_id, from_status, to_status, notes, changed_at
)
SELECT
    generated_alert.id,
    'INVESTIGATING',
    'CLOSED',
    generated_alert.resolution_notes,
    generated_alert.closed_at
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE generated_alert.status = 'CLOSED';

-- ACKNOWLEDGED -> DISMISSED.
INSERT INTO alert_history (
    alert_id, from_status, to_status, notes, changed_at
)
SELECT
    generated_alert.id,
    'ACKNOWLEDGED',
    'DISMISSED',
    generated_alert.resolution_notes,
    generated_alert.dismissed_at
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE generated_alert.status = 'DISMISSED';

-- Verification for the current execution.
SELECT
    @transaction_run_prefix AS transaction_run_prefix,
    COUNT(*) AS alert_count,
    MIN(generated_alert.created_at) AS earliest_alert_time,
    MAX(generated_alert.created_at) AS latest_alert_time
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id;

SELECT
    candidate.rule_type,
    candidate.rule_name,
    COUNT(*) AS alert_count
FROM generated_alert_candidates AS candidate
GROUP BY candidate.rule_type, candidate.rule_name
ORDER BY FIELD(
    candidate.rule_type,
    'AMOUNT_THRESHOLD',
    'VELOCITY',
    'NEW_PAYEE',
    'DAILY_LIMIT'
);

SELECT
    generated_alert.status,
    COUNT(*) AS alert_count
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
GROUP BY generated_alert.status
ORDER BY generated_alert.status;

SELECT
    candidate.rule_type,
    COUNT(*) AS related_transaction_count
FROM generated_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
JOIN alert_transactions AS relationship
  ON relationship.alert_id = generated_alert.id
GROUP BY candidate.rule_type
ORDER BY FIELD(
    candidate.rule_type,
    'AMOUNT_THRESHOLD',
    'VELOCITY',
    'NEW_PAYEE',
    'DAILY_LIMIT'
);

-- Expected final default distribution: ABNORMAL = 90, NORMAL = 5,
-- PENDING = 5. Only transactions with at least one actual rule match become
-- ABNORMAL.
SELECT
    transaction_source.status,
    COUNT(*) AS transaction_count
FROM transactions AS transaction_source
WHERE transaction_source.transaction_ref LIKE CONCAT(
    @transaction_run_prefix,
    '-%'
)
GROUP BY transaction_source.status
ORDER BY FIELD(transaction_source.status, 'PENDING', 'NORMAL', 'ABNORMAL');

-- Optional cleanup for this execution (run manually before dropping the
-- temporary candidate table):
-- DELETE generated_alert
-- FROM alerts AS generated_alert
-- JOIN generated_alert_candidates AS candidate
--   ON generated_alert.rule_id = candidate.rule_id
--  AND generated_alert.trigger_transaction_id = candidate.transaction_id;
-- alert_history and alert_transactions are removed automatically by CASCADE.

DROP TEMPORARY TABLE IF EXISTS generated_alert_candidates;
