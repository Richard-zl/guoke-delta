INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'player.deposit_amount', '打手入驻押金金额', '100', 'number', '打手配置', '开启打手押金时的应支付金额，单位：元', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'player.deposit_amount'
);
