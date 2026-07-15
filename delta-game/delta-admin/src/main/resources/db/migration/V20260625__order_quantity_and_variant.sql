CREATE TABLE IF NOT EXISTS `product_variant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '规格选项ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `name` varchar(64) NOT NULL COMMENT '规格名(如:王者局)',
  `price` decimal(10,2) NOT NULL COMMENT '该规格售价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '划线原价(可空)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品规格选项表(单维定价)';

ALTER TABLE `product`
    ADD COLUMN `quantity_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可选数量: 1开 0关' AFTER `per_user_limit_type`,
    ADD COLUMN `unit_label` varchar(16) DEFAULT NULL COMMENT '数量单位名(如:小时/局/个)' AFTER `quantity_enabled`,
    ADD COLUMN `max_quantity` int DEFAULT NULL COMMENT '最大可购数量(quantity_enabled=1时生效)' AFTER `unit_label`;

ALTER TABLE `order`
    ADD COLUMN `quantity` int NOT NULL DEFAULT 1 COMMENT '购买数量' AFTER `amount`,
    ADD COLUMN `unit_price` decimal(10,2) DEFAULT NULL COMMENT '下单单价快照(含规格价)' AFTER `quantity`,
    ADD COLUMN `variant_id` bigint DEFAULT NULL COMMENT '选中的规格选项ID(留痕,可空)' AFTER `unit_price`,
    ADD COLUMN `variant_name` varchar(64) DEFAULT NULL COMMENT '规格名快照(可空)' AFTER `variant_id`;

UPDATE `order` SET `unit_price` = `amount` WHERE `unit_price` IS NULL AND `quantity` = 1;
