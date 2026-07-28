-- MySQL 8.x: generate approximately 200 alerts for local/demo use.
-- Prerequisites:
--   1. Run src/sql/schema.sql.
--   2. Ensure at least 50 non-PENDING transactions exist. For a predictable
--      demo dataset, run src/sql/seed_transactions_100.sql first.
--
-- The script creates four reusable seed rules when they do not exist, selects
-- up to 200 rule/transaction pairs without an existing alert, then populates
-- alerts, alert_history and alert_transactions as one complete data chain.
-- Transactions selected as alert triggers are updated to ABNORMAL so the
-- generated data remains consistent with the rule-evaluation status model.

USE transaction_monitoring;

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';

SET @alert_seed_now = UTC_TIMESTAMP(3);
SET @alert_seed_run_id = CONCAT(
    DATE_FORMAT(@alert_seed_now, '%Y%m%d%H%i%s'),
    '-',
    UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8))
);

-- Prepare four stable rules used by the generated alerts.
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
    '[SEED] High-value transaction',
    'Generated amount-threshold rule for alert demo data',
    'AMOUNT_THRESHOLD',
    'HIGH',
    TRUE,
    'USD',
    10000.0000,
    NULL,
    NULL,
    NULL,
    @alert_seed_now,
    @alert_seed_now,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = '[SEED] High-value transaction'
);

INSERT INTO rules (
    name, description, type, severity, enabled, currency,
    threshold_amount, transaction_count, time_window_minutes,
    daily_limit_amount, created_at, updated_at, version
)
SELECT
    '[SEED] Rapid transaction velocity',
    'Generated velocity rule for alert demo data',
    'VELOCITY',
    'MEDIUM',
    TRUE,
    NULL,
    NULL,
    5,
    10,
    NULL,
    @alert_seed_now,
    @alert_seed_now,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = '[SEED] Rapid transaction velocity'
);

INSERT INTO rules (
    name, description, type, severity, enabled, currency,
    threshold_amount, transaction_count, time_window_minutes,
    daily_limit_amount, created_at, updated_at, version
)
SELECT
    '[SEED] New payee detected',
    'Generated new-payee rule for alert demo data',
    'NEW_PAYEE',
    'LOW',
    TRUE,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    @alert_seed_now,
    @alert_seed_now,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = '[SEED] New payee detected'
);

INSERT INTO rules (
    name, description, type, severity, enabled, currency,
    threshold_amount, transaction_count, time_window_minutes,
    daily_limit_amount, created_at, updated_at, version
)
SELECT
    '[SEED] Daily transaction limit',
    'Generated daily-limit rule for alert demo data',
    'DAILY_LIMIT',
    'HIGH',
    TRUE,
    'USD',
    NULL,
    NULL,
    NULL,
    50000.0000,
    @alert_seed_now,
    @alert_seed_now,
    0
WHERE NOT EXISTS (
    SELECT 1 FROM rules WHERE name = '[SEED] Daily transaction limit'
);

DROP TEMPORARY TABLE IF EXISTS seed_alert_candidates;

-- Select unused rule/transaction combinations. The unique constraint on
-- (rule_id, trigger_transaction_id) is therefore respected on repeated runs.
CREATE TEMPORARY TABLE seed_alert_candidates AS
SELECT
    ROW_NUMBER() OVER (
        ORDER BY transaction_source.transaction_time DESC,
                 transaction_source.id DESC,
                 rule_source.id
    ) AS sequence_no,
    rule_source.id AS rule_id,
    rule_source.name AS rule_name,
    rule_source.type AS rule_type,
    rule_source.severity,
    transaction_source.id AS transaction_id,
    transaction_source.account_id,
    transaction_source.transaction_time
FROM transactions AS transaction_source
CROSS JOIN rules AS rule_source
WHERE transaction_source.status IN ('NORMAL', 'ABNORMAL')
AND rule_source.name IN (
    '[SEED] High-value transaction',
    '[SEED] Rapid transaction velocity',
    '[SEED] New payee detected',
    '[SEED] Daily transaction limit'
)
AND NOT EXISTS (
    SELECT 1
    FROM alerts AS existing_alert
    WHERE existing_alert.rule_id = rule_source.id
      AND existing_alert.trigger_transaction_id = transaction_source.id
)
ORDER BY transaction_source.transaction_time DESC,
         transaction_source.id DESC,
         rule_source.id
LIMIT 200;

-- An alert means at least one rule flagged the transaction. Seed data bypasses
-- RabbitMQ, so apply the same final status that the result consumer would set.
UPDATE transactions AS transaction_to_update
JOIN (
    SELECT DISTINCT transaction_id
    FROM seed_alert_candidates
) AS flagged_transaction
  ON flagged_transaction.transaction_id = transaction_to_update.id
SET transaction_to_update.status = 'ABNORMAL',
    transaction_to_update.updated_at = @alert_seed_now
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
    CONCAT(
        'Generated alert ',
        LPAD(candidate.sequence_no, 3, '0'),
        ' for transaction ',
        candidate.transaction_id,
        ' [seed-run=',
        @alert_seed_run_id,
        ']'
    ) AS description,
    CASE MOD(candidate.sequence_no - 1, 5)
        WHEN 3 THEN 'Reviewed and confirmed as legitimate activity.'
        WHEN 4 THEN 'Dismissed as a generated false-positive example.'
        ELSE NULL
    END AS resolution_notes,
    DATE_SUB(
        @alert_seed_now,
        INTERVAL (candidate.sequence_no * 13) MINUTE
    ) AS created_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) IN (1, 2, 3, 4)
            THEN DATE_ADD(
                DATE_SUB(
                    @alert_seed_now,
                    INTERVAL (candidate.sequence_no * 13) MINUTE
                ),
                INTERVAL 5 MINUTE
            )
        ELSE NULL
    END AS acknowledged_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) IN (2, 3)
            THEN DATE_ADD(
                DATE_SUB(
                    @alert_seed_now,
                    INTERVAL (candidate.sequence_no * 13) MINUTE
                ),
                INTERVAL 15 MINUTE
            )
        ELSE NULL
    END AS investigating_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) = 3
            THEN DATE_ADD(
                DATE_SUB(
                    @alert_seed_now,
                    INTERVAL (candidate.sequence_no * 13) MINUTE
                ),
                INTERVAL 45 MINUTE
            )
        ELSE NULL
    END AS closed_at,
    CASE
        WHEN MOD(candidate.sequence_no - 1, 5) = 4
            THEN DATE_ADD(
                DATE_SUB(
                    @alert_seed_now,
                    INTERVAL (candidate.sequence_no * 13) MINUTE
                ),
                INTERVAL 30 MINUTE
            )
        ELSE NULL
    END AS dismissed_at,
    CASE MOD(candidate.sequence_no - 1, 5)
        WHEN 0 THEN DATE_SUB(
            @alert_seed_now,
            INTERVAL (candidate.sequence_no * 13) MINUTE
        )
        WHEN 1 THEN DATE_ADD(
            DATE_SUB(
                @alert_seed_now,
                INTERVAL (candidate.sequence_no * 13) MINUTE
            ),
            INTERVAL 5 MINUTE
        )
        WHEN 2 THEN DATE_ADD(
            DATE_SUB(
                @alert_seed_now,
                INTERVAL (candidate.sequence_no * 13) MINUTE
            ),
            INTERVAL 15 MINUTE
        )
        WHEN 3 THEN DATE_ADD(
            DATE_SUB(
                @alert_seed_now,
                INTERVAL (candidate.sequence_no * 13) MINUTE
            ),
            INTERVAL 45 MINUTE
        )
        ELSE DATE_ADD(
            DATE_SUB(
                @alert_seed_now,
                INTERVAL (candidate.sequence_no * 13) MINUTE
            ),
            INTERVAL 30 MINUTE
        )
    END AS updated_at,
    0 AS version
FROM seed_alert_candidates AS candidate;

-- Relate each alert to its triggering transaction for the alert-detail page.
INSERT INTO alert_transactions (alert_id, transaction_id)
SELECT
    generated_alert.id,
    candidate.transaction_id
FROM seed_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id;

-- Initial lifecycle entry: NULL -> OPEN.
INSERT INTO alert_history (
    alert_id, from_status, to_status, notes, changed_at
)
SELECT
    generated_alert.id,
    NULL,
    'OPEN',
    'Alert generated by seed data script.',
    generated_alert.created_at
FROM seed_alert_candidates AS candidate
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
FROM seed_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE MOD(candidate.sequence_no - 1, 5) IN (1, 2, 3, 4);

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
FROM seed_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE MOD(candidate.sequence_no - 1, 5) IN (2, 3);

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
FROM seed_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE MOD(candidate.sequence_no - 1, 5) = 3;

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
FROM seed_alert_candidates AS candidate
JOIN alerts AS generated_alert
  ON generated_alert.rule_id = candidate.rule_id
 AND generated_alert.trigger_transaction_id = candidate.transaction_id
WHERE MOD(candidate.sequence_no - 1, 5) = 4;

-- Verification for the current execution. Expected alert_count: 200 when at
-- least 200 unused rule/transaction pairs are available.
SELECT
    @alert_seed_run_id AS seed_run_id,
    COUNT(*) AS alert_count,
    MIN(created_at) AS earliest_alert_time,
    MAX(created_at) AS latest_alert_time
FROM alerts
WHERE description LIKE CONCAT(
    '%[seed-run=',
    @alert_seed_run_id,
    ']%'
);

SELECT
    status,
    COUNT(*) AS alert_count
FROM alerts
WHERE description LIKE CONCAT(
    '%[seed-run=',
    @alert_seed_run_id,
    ']%'
)
GROUP BY status
ORDER BY status;

SELECT
    COUNT(*) AS history_count
FROM alert_history AS history
JOIN alerts AS generated_alert ON generated_alert.id = history.alert_id
WHERE generated_alert.description LIKE CONCAT(
    '%[seed-run=',
    @alert_seed_run_id,
    ']%'
);

SELECT
    COUNT(*) AS transaction_relationship_count
FROM alert_transactions AS relationship
JOIN alerts AS generated_alert ON generated_alert.id = relationship.alert_id
WHERE generated_alert.description LIKE CONCAT(
    '%[seed-run=',
    @alert_seed_run_id,
    ']%'
);

-- Every transaction referenced by this seed run should now be ABNORMAL.
SELECT
    COUNT(DISTINCT relationship.transaction_id)
        AS triggering_transaction_count,
    COUNT(DISTINCT CASE
        WHEN triggering_transaction.status = 'ABNORMAL'
            THEN relationship.transaction_id
    END) AS abnormal_transaction_count
FROM alert_transactions AS relationship
JOIN alerts AS generated_alert ON generated_alert.id = relationship.alert_id
JOIN transactions AS triggering_transaction
  ON triggering_transaction.id = relationship.transaction_id
WHERE generated_alert.description LIKE CONCAT(
    '%[seed-run=',
    @alert_seed_run_id,
    ']%'
);

DROP TEMPORARY TABLE IF EXISTS seed_alert_candidates;

-- Optional cleanup for this execution (run manually before the session ends):
-- DELETE FROM alerts
-- WHERE description LIKE CONCAT(
--     '%[seed-run=', @alert_seed_run_id, ']%'
-- );
-- alert_history and alert_transactions are removed automatically by CASCADE.
-- Triggering transactions remain ABNORMAL because they may be referenced by
-- alerts from another run; restore them manually only when that is intended.
