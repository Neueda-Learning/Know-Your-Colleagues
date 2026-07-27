-- Transaction Monitoring 项目统一数据库初始化脚本（MySQL 8.x）
-- 当前覆盖实体：Transaction、Alert、AlertHistory、AlertTransaction。
-- 应用内部约定所有业务时间均使用 UTC。

CREATE DATABASE IF NOT EXISTS transaction_monitoring
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE transaction_monitoring;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================
-- 1. 交易表
-- ============================================================
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库内部主键',
    transaction_ref VARCHAR(64) NOT NULL COMMENT '后端生成的唯一交易号',
    account_id VARCHAR(64) NOT NULL COMMENT '发起交易的账户编号',
    payee_id VARCHAR(64) NOT NULL COMMENT '收款人或交易对手编号',
    amount DECIMAL(19, 4) NOT NULL COMMENT '交易金额',
    currency CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码',
    transaction_type VARCHAR(16) NOT NULL COMMENT '交易类型：DEBIT、CREDIT',
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
        COMMENT '交易状态：PENDING、COMPLETED、FAILED',
    description VARCHAR(500) NULL COMMENT '交易描述或附言',
    transaction_time DATETIME(3) NOT NULL COMMENT '交易发生时间（UTC）',
    created_at DATETIME(3) NOT NULL COMMENT '记录创建时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '记录最后更新时间（UTC）',

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
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '交易记录表';