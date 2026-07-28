-- Transaction Monitoring unified database schema (MySQL 8.x).
-- Business timestamps are stored and interpreted as UTC.

CREATE DATABASE IF NOT EXISTS transaction_monitoring
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE transaction_monitoring;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================
-- 1. Transactions
-- Corresponds to entity.Transaction (@TableName("transactions")).
-- ============================================================
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Database primary key',
    transaction_ref VARCHAR(64) NOT NULL
        COMMENT 'Unique business transaction reference',
    account_id VARCHAR(64) NOT NULL
        COMMENT 'Account that initiated the transaction',
    payee_id VARCHAR(64) NOT NULL
        COMMENT 'Payee or counterparty identifier',
    amount DECIMAL(19, 4) NOT NULL
        COMMENT 'Transaction amount',
    currency CHAR(3) NOT NULL
        COMMENT 'ISO 4217 currency code',
    transaction_type VARCHAR(16) NOT NULL
        COMMENT 'DEBIT or CREDIT',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING, NORMAL or ABNORMAL',
    description VARCHAR(500) NULL
        COMMENT 'Transaction description',
    transaction_time DATETIME(3) NOT NULL
        COMMENT 'Transaction occurrence time in UTC',
    created_at DATETIME(3) NOT NULL
        COMMENT 'Creation time in UTC',
    updated_at DATETIME(3) NOT NULL
        COMMENT 'Last update time in UTC',

    PRIMARY KEY (id),
    UNIQUE KEY uk_transactions_transaction_ref (transaction_ref),
    KEY idx_transactions_account_time (account_id, transaction_time, id),
    KEY idx_transactions_payee_time (payee_id, transaction_time, id),
    KEY idx_transactions_status_time (status, transaction_time, id),
    KEY idx_transactions_transaction_time (transaction_time, id),
    KEY idx_transactions_amount (amount),

    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_currency_uppercase
        CHECK (currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT chk_transactions_type
        CHECK (transaction_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_transactions_status
        CHECK (status IN ('PENDING', 'NORMAL', 'ABNORMAL'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Transaction records';

-- Migrate databases created with the former PENDING/COMPLETED/FAILED model.
-- Dropping the old check first allows the legacy values to be converted.
ALTER TABLE transactions
    DROP CHECK chk_transactions_status;

UPDATE transactions
SET status = CASE status
    WHEN 'COMPLETED' THEN 'NORMAL'
    WHEN 'FAILED' THEN 'ABNORMAL'
    ELSE status
END
WHERE status IN ('COMPLETED', 'FAILED');

ALTER TABLE transactions
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_status
        CHECK (status IN ('PENDING', 'NORMAL', 'ABNORMAL'));

-- ============================================================
-- 2. Monitoring rules
-- Corresponds to entity.Rule (@TableName("rules")).
-- ============================================================
CREATE TABLE IF NOT EXISTS rules (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Database primary key',
    name VARCHAR(128) NOT NULL
        COMMENT 'Rule name',
    description VARCHAR(1000) NULL
        COMMENT 'Rule description',
    type VARCHAR(32) NOT NULL
        COMMENT 'AMOUNT_THRESHOLD, VELOCITY, NEW_PAYEE or DAILY_LIMIT',
    severity VARCHAR(16) NOT NULL
        COMMENT 'LOW, MEDIUM or HIGH',
    enabled BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT 'Whether this rule participates in evaluation',
    currency CHAR(3) NULL
        COMMENT 'Optional ISO 4217 currency filter',
    threshold_amount DECIMAL(19, 4) NULL
        COMMENT 'Amount threshold used by AMOUNT_THRESHOLD',
    transaction_count INT UNSIGNED NULL
        COMMENT 'Allowed transaction count used by VELOCITY',
    time_window_minutes INT UNSIGNED NULL
        COMMENT 'Rolling window in minutes used by VELOCITY',
    daily_limit_amount DECIMAL(19, 4) NULL
        COMMENT 'Daily amount limit used by DAILY_LIMIT',
    created_at DATETIME(3) NOT NULL
        COMMENT 'Creation time in UTC',
    updated_at DATETIME(3) NOT NULL
        COMMENT 'Last update time in UTC',
    version INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'Optimistic-lock version',

    PRIMARY KEY (id),
    KEY idx_rules_enabled_type (enabled, type),
    KEY idx_rules_severity (severity),
    KEY idx_rules_created_at (created_at, id),

    CONSTRAINT chk_rules_type CHECK (
        type IN (
            'AMOUNT_THRESHOLD',
            'VELOCITY',
            'NEW_PAYEE',
            'DAILY_LIMIT'
        )
    ),
    CONSTRAINT chk_rules_severity
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_rules_currency CHECK (
        currency IS NULL OR currency REGEXP '^[A-Z]{3}$'
    ),
    CONSTRAINT chk_rules_threshold_amount CHECK (
        threshold_amount IS NULL OR threshold_amount > 0
    ),
    CONSTRAINT chk_rules_transaction_count CHECK (
        transaction_count IS NULL OR transaction_count > 0
    ),
    CONSTRAINT chk_rules_time_window CHECK (
        time_window_minutes IS NULL OR time_window_minutes > 0
    ),
    CONSTRAINT chk_rules_daily_limit CHECK (
        daily_limit_amount IS NULL OR daily_limit_amount > 0
    ),
    CONSTRAINT chk_rules_required_parameters CHECK (
        (type = 'AMOUNT_THRESHOLD' AND threshold_amount IS NOT NULL)
        OR (
            type = 'VELOCITY'
            AND transaction_count IS NOT NULL
            AND time_window_minutes IS NOT NULL
        )
        OR type = 'NEW_PAYEE'
        OR (type = 'DAILY_LIMIT' AND daily_limit_amount IS NOT NULL)
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Configurable transaction-monitoring rules';

-- ============================================================
-- 3. Alerts
-- Corresponds to entity.Alert (@TableName("alerts")).
-- rule_name is a snapshot retained if the rule is later renamed.
-- ============================================================
CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Database primary key',
    rule_id BIGINT UNSIGNED NOT NULL
        COMMENT 'Rule that generated this alert',
    trigger_transaction_id BIGINT UNSIGNED NOT NULL
        COMMENT 'Transaction that caused the rule to trigger',
    account_id VARCHAR(64) NOT NULL
        COMMENT 'Account evaluated by the rule',
    rule_name VARCHAR(128) NOT NULL
        COMMENT 'Rule-name snapshot at alert creation time',
    severity VARCHAR(16) NOT NULL
        COMMENT 'LOW, MEDIUM or HIGH',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        COMMENT 'Alert lifecycle status',
    title VARCHAR(255) NOT NULL
        COMMENT 'Short alert title',
    description VARCHAR(1000) NULL
        COMMENT 'Rule-evaluation details',
    resolution_notes VARCHAR(2000) NULL
        COMMENT 'Resolution notes for CLOSED or DISMISSED alerts',
    created_at DATETIME(3) NOT NULL
        COMMENT 'Alert creation time in UTC',
    acknowledged_at DATETIME(3) NULL
        COMMENT 'Time the alert entered ACKNOWLEDGED',
    investigating_at DATETIME(3) NULL
        COMMENT 'Time the alert entered INVESTIGATING',
    closed_at DATETIME(3) NULL
        COMMENT 'Time the alert entered CLOSED',
    dismissed_at DATETIME(3) NULL
        COMMENT 'Time the alert entered DISMISSED',
    updated_at DATETIME(3) NOT NULL
        COMMENT 'Last update time in UTC',
    version INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'Optimistic-lock version',

    PRIMARY KEY (id),
    UNIQUE KEY uk_alerts_rule_trigger (
        rule_id,
        trigger_transaction_id
    ),
    KEY idx_alerts_status_created (status, created_at, id),
    KEY idx_alerts_severity_created (severity, created_at, id),
    KEY idx_alerts_account_created (account_id, created_at, id),
    KEY idx_alerts_trigger_transaction (trigger_transaction_id),

    CONSTRAINT fk_alerts_rule
        FOREIGN KEY (rule_id) REFERENCES rules (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_alerts_trigger_transaction
        FOREIGN KEY (trigger_transaction_id) REFERENCES transactions (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_alerts_severity
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_alerts_status CHECK (
        status IN (
            'OPEN',
            'ACKNOWLEDGED',
            'INVESTIGATING',
            'CLOSED',
            'DISMISSED'
        )
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Alerts generated by monitoring rules';

-- ============================================================
-- 4. Alert status history
-- Corresponds to entity.AlertHistory (@TableName("alert_history")).
-- from_status is NULL for the initial NULL -> OPEN transition.
-- ============================================================
CREATE TABLE IF NOT EXISTS alert_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Database primary key',
    alert_id BIGINT UNSIGNED NOT NULL
        COMMENT 'Associated alert',
    from_status VARCHAR(20) NULL
        COMMENT 'Previous status; NULL for initial creation',
    to_status VARCHAR(20) NOT NULL
        COMMENT 'Status after the change',
    notes VARCHAR(2000) NULL
        COMMENT 'Status-change notes',
    changed_at DATETIME(3) NOT NULL
        COMMENT 'Status-change time in UTC',

    PRIMARY KEY (id),
    KEY idx_alert_history_alert_changed (alert_id, changed_at, id),

    CONSTRAINT fk_alert_history_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT chk_alert_history_from_status CHECK (
        from_status IS NULL
        OR from_status IN (
            'OPEN',
            'ACKNOWLEDGED',
            'INVESTIGATING',
            'CLOSED',
            'DISMISSED'
        )
    ),
    CONSTRAINT chk_alert_history_to_status CHECK (
        to_status IN (
            'OPEN',
            'ACKNOWLEDGED',
            'INVESTIGATING',
            'CLOSED',
            'DISMISSED'
        )
    )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Alert lifecycle transition history';

-- ============================================================
-- 5. Alert-to-transaction relationships
-- Corresponds to entity.AlertTransaction
-- (@TableName("alert_transactions")).
-- ============================================================
CREATE TABLE IF NOT EXISTS alert_transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
        COMMENT 'Database primary key',
    alert_id BIGINT UNSIGNED NOT NULL
        COMMENT 'Associated alert',
    transaction_id BIGINT UNSIGNED NOT NULL
        COMMENT 'Transaction related to the alert evaluation',

    PRIMARY KEY (id),
    UNIQUE KEY uk_alert_transactions_alert_transaction (
        alert_id,
        transaction_id
    ),
    KEY idx_alert_transactions_transaction (transaction_id, alert_id),

    CONSTRAINT fk_alert_transactions_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    CONSTRAINT fk_alert_transactions_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Many-to-many relationship between alerts and transactions';
