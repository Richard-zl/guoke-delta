INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'trial.root_category_id', '体验单根分类ID', '38', 'number', '订单配置', '超值体验单根分类ID，其下所有子分类商品均视为体验单', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'trial.root_category_id'
);

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'trial.limit_period', '体验单限购周期', '1', 'select', '订单配置',
       '[{"label":"不限购","value":"0"},{"label":"1天1单","value":"1"},{"label":"2天1单","value":"2"},{"label":"一周1单","value":"7"},{"label":"一月1单","value":"30"}]',
       NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'trial.limit_period'
);
