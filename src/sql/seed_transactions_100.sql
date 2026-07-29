-- MySQL 8.x: generate 100 rule-linked transactions for local/demo use.
-- Prerequisite: run src/sql/schema.sql first.
--
-- Each execution creates a unique data run containing ten account scenarios.
-- Every scenario has ten chronological transactions:
--   * positions 1-9 use different payees and an amount derived from the first
--     enabled AMOUNT_THRESHOLD and DAILY_LIMIT rules;
--   * position 10 reuses the first payee and stays below both amount limits;
--   * position 10 remains PENDING for the first five accounts and is NORMAL
--     for the remaining five accounts.
--
-- With the default rules, running seed_alerts_200.sql afterwards produces:
--   * 90 AMOUNT_THRESHOLD matches;
--   * 90 NEW_PAYEE matches;
--   * 10 VELOCITY matches (the sixth transaction for each account);
--   * 10 DAILY_LIMIT matches (the first cumulative limit crossing).
--
-- This script writes directly to MySQL and does not publish RabbitMQ events.

USE transaction_monitoring;

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';

SET @data_now = UTC_TIMESTAMP(3);
SET @data_run_id = CONCAT(
    DATE_FORMAT(@data_now, '%Y%m%d%H%i%s'),
    '-',
    UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8))
);

-- Use the first enabled rule of each type. The fallback values match the
-- default rules created by seed_alerts_200.sql when a type does not yet exist.
SET @amount_threshold = COALESCE((
    SELECT threshold_amount
    FROM rules
    WHERE enabled = TRUE
      AND type = 'AMOUNT_THRESHOLD'
    ORDER BY id
    LIMIT 1
), 10000.0000);

SET @daily_limit = COALESCE((
    SELECT daily_limit_amount
    FROM rules
    WHERE enabled = TRUE
      AND type = 'DAILY_LIMIT'
    ORDER BY id
    LIMIT 1
), 50000.0000);

SET @scenario_currency = COALESCE((
    SELECT currency
    FROM rules
    WHERE enabled = TRUE
      AND type = 'AMOUNT_THRESHOLD'
      AND currency IS NOT NULL
    ORDER BY id
    LIMIT 1
), (
    SELECT currency
    FROM rules
    WHERE enabled = TRUE
      AND type = 'DAILY_LIMIT'
      AND currency IS NOT NULL
    ORDER BY id
    LIMIT 1
), 'USD');

-- Nine transactions per account exceed the amount threshold. Four such
-- transactions also exceed the configured daily limit cumulatively when the
-- two selected rules use the same currency (as the default rules do).
SET @rule_trigger_amount = CAST(
    GREATEST(
        @amount_threshold + 100.0000,
        (@daily_limit / 4) + 100.0000
    ) AS DECIMAL(19, 4)
);

SET @normal_amount = CAST(
    GREATEST(
        1.0000,
        LEAST(
            100.0000,
            @amount_threshold / 10,
            @daily_limit / 100
        )
    ) AS DECIMAL(19, 4)
);

-- Keep all ten positions on the current UTC date and within a ten-minute
-- window. Different accounts may share timestamps, which does not affect rule
-- evaluation because velocity and daily-limit rules are account-scoped.
SET @scenario_start = GREATEST(
    DATE_SUB(@data_now, INTERVAL 9 MINUTE),
    CAST(UTC_DATE() AS DATETIME)
);

INSERT INTO transactions (
    transaction_ref,
    account_id,
    payee_id,
    amount,
    currency,
    transaction_type,
    status,
    description,
    transaction_time,
    created_at,
    updated_at
)
WITH RECURSIVE sequence_numbers AS (
    SELECT 1 AS sequence_no
    UNION ALL
    SELECT sequence_no + 1
    FROM sequence_numbers
    WHERE sequence_no < 100
), scenario_rows AS (
    SELECT
        sequence_no,
        FLOOR((sequence_no - 1) / 10) + 1 AS account_no,
        MOD(sequence_no - 1, 10) + 1 AS position_no
    FROM sequence_numbers
)
SELECT
    CONCAT(
        'TXN-',
        @data_run_id,
        '-',
        LPAD(sequence_no, 3, '0')
    ) AS transaction_ref,
    CONCAT(
        'ACC-',
        @data_run_id,
        '-',
        LPAD(account_no, 2, '0')
    ) AS account_id,
    CASE
        WHEN position_no = 10 THEN CONCAT(
            'PAYEE-',
            @data_run_id,
            '-',
            LPAD(account_no, 2, '0'),
            '-01'
        )
        ELSE CONCAT(
            'PAYEE-',
            @data_run_id,
            '-',
            LPAD(account_no, 2, '0'),
            '-',
            LPAD(position_no, 2, '0')
        )
    END AS payee_id,
    CASE
        WHEN position_no <= 9 THEN @rule_trigger_amount
        ELSE @normal_amount
    END AS amount,
    @scenario_currency AS currency,
    'DEBIT' AS transaction_type,
    CASE
        WHEN position_no = 10 AND account_no <= 5 THEN 'PENDING'
        ELSE 'NORMAL'
    END AS status,
    CASE
        WHEN position_no <= 9 THEN CONCAT(
            'Rule-linked high-value payment ',
            position_no,
            ' of 9'
        )
        WHEN account_no <= 5 THEN 'Known-payee payment awaiting evaluation'
        ELSE 'Known-payee low-value payment'
    END AS description,
    DATE_ADD(
        @scenario_start,
        INTERVAL (position_no - 1) MINUTE
    ) AS transaction_time,
    @data_now AS created_at,
    @data_now AS updated_at
FROM scenario_rows;

-- Verification for the current execution. Before the alert script runs, the
-- expected distribution is NORMAL = 95 and PENDING = 5.
SELECT
    @data_run_id AS data_run_id,
    @scenario_currency AS scenario_currency,
    @rule_trigger_amount AS rule_trigger_amount,
    @normal_amount AS normal_amount,
    COUNT(*) AS inserted_rows,
    MIN(transaction_time) AS earliest_transaction_time,
    MAX(transaction_time) AS latest_transaction_time
FROM transactions
WHERE transaction_ref LIKE CONCAT('TXN-', @data_run_id, '-%');

SELECT
    status,
    COUNT(*) AS transaction_count
FROM transactions
WHERE transaction_ref LIKE CONCAT('TXN-', @data_run_id, '-%')
GROUP BY status
ORDER BY FIELD(status, 'PENDING', 'NORMAL', 'ABNORMAL');

SELECT
    account_id,
    COUNT(*) AS transaction_count,
    COUNT(DISTINCT payee_id) AS distinct_payees,
    SUM(amount) AS total_amount
FROM transactions
WHERE transaction_ref LIKE CONCAT('TXN-', @data_run_id, '-%')
GROUP BY account_id
ORDER BY account_id;

-- Optional cleanup for this execution (run manually only when needed):
-- DELETE FROM transactions
-- WHERE transaction_ref LIKE CONCAT('TXN-', @data_run_id, '-%');
