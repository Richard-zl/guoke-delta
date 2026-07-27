-- 会员积分体系重构：订单折扣快照、积分流水账户类型、排除分类配置、历史积分×7

-- 订单折扣快照字段
ALTER TABLE `order`
    ADD COLUMN `original_amount` decimal(10,2) DEFAULT NULL COMMENT '下单原价(等级折/券折前)' AFTER `variant_name`,
    ADD COLUMN `member_discount_rate` decimal(5,4) DEFAULT NULL COMMENT '会员折扣率快照' AFTER `original_amount`,
    ADD COLUMN `member_discount_amount` decimal(10,2) DEFAULT NULL COMMENT '会员折扣减免金额' AFTER `member_discount_rate`,
    ADD COLUMN `member_level_name` varchar(64) DEFAULT NULL COMMENT '会员等级名称快照' AFTER `member_discount_amount`,
    ADD COLUMN `coupon_discount_amount` decimal(10,2) DEFAULT NULL COMMENT '优惠券抵扣金额' AFTER `member_level_name`;

-- 积分流水账户类型
ALTER TABLE `points_detail`
    ADD COLUMN `account_type` varchar(16) NOT NULL DEFAULT 'CURRENT' COMMENT '账户: CURRENT/TOTAL' AFTER `order_id`;

-- 会员折扣排除分类（任务/定制等，运营可在后台改）
INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'member.discount_exclude_category_ids', '会员折扣排除分类ID', '', 'text', '会员配置',
       '逗号分隔的分类ID，其下所有子分类商品不享受会员等级折扣（如任务、定制单）', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'member.discount_exclude_category_ids'
);

-- 历史积分×7（幂等：仅执行一次）
UPDATE `user`
SET `points` = `points` * 7,
    `total_points` = `total_points` * 7
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'member.points_migrated_x7' AND `config_value` = '1'
);

UPDATE `points_detail`
SET `points` = `points` * 7,
    `balance` = `balance` * 7
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'member.points_migrated_x7' AND `config_value` = '1'
);

-- 按新阈值重算等级
UPDATE `user` SET `level_code` = 'BRONZE', `level_name` = '青铜伴星' WHERE `total_points` < 10000;
UPDATE `user` SET `level_code` = 'SILVER', `level_name` = '白银伴星' WHERE `total_points` >= 10000 AND `total_points` < 35000;
UPDATE `user` SET `level_code` = 'GOLD', `level_name` = '黄金伴星' WHERE `total_points` >= 35000 AND `total_points` < 105000;
UPDATE `user` SET `level_code` = 'PLATINUM', `level_name` = '铂金伴星' WHERE `total_points` >= 105000 AND `total_points` < 245000;
UPDATE `user` SET `level_code` = 'DIAMOND', `level_name` = '钻石伴星' WHERE `total_points` >= 245000 AND `total_points` < 560000;
UPDATE `user` SET `level_code` = 'KING', `level_name` = '王者伴星' WHERE `total_points` >= 560000;

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'member.points_migrated_x7', '历史积分已×7迁移', '1', 'boolean', '会员配置',
       '幂等标记，勿手动改为0后重复执行迁移脚本', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'member.points_migrated_x7'
);
