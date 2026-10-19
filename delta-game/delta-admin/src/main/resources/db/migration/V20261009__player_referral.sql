ALTER TABLE `order`
  ADD COLUMN `referral_player_id` BIGINT NULL COMMENT '转介绍打手ID' AFTER `settle_available_at`,
  ADD COLUMN `referral_rate` DECIMAL(8,4) NULL COMMENT '确认时介绍比例，0.0500表示5%' AFTER `referral_player_id`,
  ADD COLUMN `referral_amount` DECIMAL(12,2) NULL COMMENT '实际介绍费' AFTER `referral_rate`,
  ADD COLUMN `referral_clawed_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '已扣回介绍费' AFTER `referral_amount`;

CREATE TABLE `player_referral` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `player_id` BIGINT NOT NULL COMMENT '介绍打手',
  `user_id` BIGINT NOT NULL COMMENT '被介绍客户',
  `rate` DECIMAL(8,4) NOT NULL COMMENT '介绍比例，0.0500表示5%',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE有效 UNBOUND已解绑',
  `active_key` BIGINT NOT NULL DEFAULT 0 COMMENT '有效为0，解绑后改为本行id',
  `bound_at` DATETIME NOT NULL COMMENT '绑定时间',
  `unbound_at` DATETIME NULL COMMENT '解绑时间',
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_referral_user_active` (`user_id`, `active_key`),
  KEY `idx_referral_player` (`player_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打手转介绍客户';
