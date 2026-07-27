-- 会员积分体系重构（与 Flyway V20260722 同步，便于手工执行）
-- 若已通过 Flyway 执行可跳过

ALTER TABLE `order`
    ADD COLUMN IF NOT EXISTS `original_amount` decimal(10,2) DEFAULT NULL COMMENT '下单原价(等级折/券折前)',
    ADD COLUMN IF NOT EXISTS `member_discount_rate` decimal(5,4) DEFAULT NULL COMMENT '会员折扣率快照',
    ADD COLUMN IF NOT EXISTS `member_discount_amount` decimal(10,2) DEFAULT NULL COMMENT '会员折扣减免金额',
    ADD COLUMN IF NOT EXISTS `member_level_name` varchar(64) DEFAULT NULL COMMENT '会员等级名称快照',
    ADD COLUMN IF NOT EXISTS `coupon_discount_amount` decimal(10,2) DEFAULT NULL COMMENT '优惠券抵扣金额';

ALTER TABLE `points_detail`
    ADD COLUMN IF NOT EXISTS `account_type` varchar(16) NOT NULL DEFAULT 'CURRENT' COMMENT '账户: CURRENT/TOTAL';

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'member.discount_exclude_category_ids', '会员折扣排除分类ID', '', 'text', '会员配置',
       '逗号分隔的分类ID，其下所有子分类商品不享受会员等级折扣（如任务、定制单）', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` WHERE `config_key` = 'member.discount_exclude_category_ids'
);

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
