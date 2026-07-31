ALTER TABLE `player_wallet`
  ADD COLUMN `pending_balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '待入账金额' AFTER `balance`;

ALTER TABLE `order`
  ADD COLUMN `settle_available_at` DATETIME NULL COMMENT '预计入账时间（确认+延迟天数）' AFTER `settle_time`;

-- settled: 0未结算 1已入账 2待入账
CREATE INDEX `idx_order_pending_settle` ON `order` (`settled`, `settle_available_at`);

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'settlement.delay_days', '结算延迟入账天数', '5', 'number', '结算配置', '用户确认后满N天转入可提现', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'settlement.delay_days');

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'withdraw.time_windows', '提现时间窗口',
       '[{"startDow":2,"startTime":"12:00","endDow":3,"endTime":"12:00"},{"startDow":6,"startTime":"12:00","endDow":7,"endTime":"12:00"}]',
       'text', '提现配置', 'JSON数组，dow:1=周一..7=周日，区间左闭右开', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'withdraw.time_windows');
