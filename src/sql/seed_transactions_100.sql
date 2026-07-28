-- MySQL 8.x: generate 100 transaction records for local/demo use.
-- Prerequisite: run src/sql/schema.sql first.
--
-- Each execution creates a new seed run and inserts another 100 rows.
-- The generated transaction_ref contains @seed_run_id, so it remains unique
-- and the inserted rows can be identified after the script finishes.
-- This script writes directly to MySQL and therefore does not publish RabbitMQ
-- events. It deliberately generates PENDING, NORMAL and ABNORMAL records for
-- transaction-list and filtering demonstrations.

USE transaction_monitoring;

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET time_zone = '+00:00';

SET @seed_now = UTC_TIMESTAMP(3);
SET @seed_run_id = CONCAT(
    DATE_FORMAT(@seed_now, '%Y%m%d%H%i%s'),
    '-',
    UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8))
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
)
SELECT
    CONCAT(
        'TXN-SEED-',
        @seed_run_id,
        '-',
        LPAD(sequence_no, 3, '0')
    ) AS transaction_ref,
    CONCAT('ACC-', LPAD(MOD(sequence_no - 1, 10) + 1, 3, '0'))
        AS account_id,
    CONCAT('PAYEE-', LPAD(MOD(sequence_no * 7, 20) + 1, 3, '0'))
        AS payee_id,
    CAST(
        CASE
            -- Regularly include high-value transactions for threshold rules.
            WHEN MOD(sequence_no, 20) = 0
                THEN 10000 + sequence_no * 137.25
            ELSE 10 + MOD(sequence_no * 7919, 800000) / 100
        END
        AS DECIMAL(19, 4)
    ) AS amount,
    CASE MOD(sequence_no, 5)
        WHEN 0 THEN 'USD'
        WHEN 1 THEN 'CNY'
        WHEN 2 THEN 'EUR'
        WHEN 3 THEN 'GBP'
        ELSE 'JPY'
    END AS currency,
    CASE
        WHEN MOD(sequence_no, 4) = 0 THEN 'CREDIT'
        ELSE 'DEBIT'
    END AS transaction_type,
    CASE
        -- High-value examples are treated as already flagged transactions.
        WHEN MOD(sequence_no, 20) = 0 THEN 'ABNORMAL'
        -- Keep several records waiting for asynchronous rule evaluation.
        WHEN MOD(sequence_no, 10) = 0 THEN 'PENDING'
        -- Add more abnormal examples for dashboard and alert demonstrations.
        WHEN MOD(sequence_no, 7) = 0 THEN 'ABNORMAL'
        ELSE 'NORMAL'
    END AS status,
    CONCAT(
        'Generated transaction ',
        LPAD(sequence_no, 3, '0'),
        ' [seed-run=',
        @seed_run_id,
        ']'
    ) AS description,
    DATE_SUB(
        @seed_now,
        INTERVAL ((100 - sequence_no) * 7) MINUTE
    ) AS transaction_time,
    @seed_now AS created_at,
    @seed_now AS updated_at
FROM sequence_numbers;

-- Verification for the current execution. Expected result: inserted_rows = 100.
SELECT
    @seed_run_id AS seed_run_id,
    COUNT(*) AS inserted_rows,
    MIN(transaction_time) AS earliest_transaction_time,
    MAX(transaction_time) AS latest_transaction_time,
    SUM(amount) AS total_amount
FROM transactions
WHERE transaction_ref LIKE CONCAT('TXN-SEED-', @seed_run_id, '-%');

-- Expected distribution for this execution:
-- NORMAL = 77, ABNORMAL = 18, PENDING = 5.
SELECT
    status,
    COUNT(*) AS transaction_count
FROM transactions
WHERE transaction_ref LIKE CONCAT('TXN-SEED-', @seed_run_id, '-%')
GROUP BY status
ORDER BY FIELD(status, 'PENDING', 'NORMAL', 'ABNORMAL');

-- Optional cleanup for this execution (run manually only when needed):
-- DELETE FROM transactions
-- WHERE transaction_ref LIKE CONCAT('TXN-SEED-', @seed_run_id, '-%');
