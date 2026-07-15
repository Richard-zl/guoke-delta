-- 将体验单根分类从旧默认值 4 修正为「超值体验单」分类 ID 38
UPDATE `sys_config`
SET `config_value` = '38',
    `remark`       = '超值体验单根分类ID，其下所有子分类商品均视为体验单'
WHERE `config_key` = 'trial.root_category_id'
  AND `config_value` = '4';

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'trial.additional_category_ids', '体验单额外分类ID', '', 'text', '订单配置',
       '逗号分隔的商品分类ID；当分类未挂在体验单根分类下时，在此补充', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'trial.additional_category_ids'
);
