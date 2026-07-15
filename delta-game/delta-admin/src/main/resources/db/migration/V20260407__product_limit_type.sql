ALTER TABLE `product`
    ADD COLUMN `per_user_limit_type` TINYINT NOT NULL DEFAULT '0' COMMENT '每人限购类型: 0-不限购 1-永久限购一次 2-7天限购一次 3-1个月限购一次' AFTER `per_user_limit_count`;

UPDATE `product`
SET `per_user_limit_type` = CASE
    WHEN `per_user_limit_enabled` = 1 AND COALESCE(`per_user_limit_count`, 0) > 0 THEN 1
    ELSE 0
END;
