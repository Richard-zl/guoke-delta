-- 打手风采（第一期）
ALTER TABLE `player`
  ADD COLUMN `intro_voice_url` varchar(512) NOT NULL DEFAULT '' COMMENT '语音介绍URL' AFTER `is_online`,
  ADD COLUMN `intro_voice_seconds` int NOT NULL DEFAULT 0 COMMENT '语音秒数' AFTER `intro_voice_url`,
  ADD COLUMN `highlight_images` varchar(4096) NOT NULL DEFAULT '' COMMENT '高光图库URL逗号分隔最多9张' AFTER `intro_voice_seconds`;

ALTER TABLE `review`
  ADD COLUMN `hide_in_showcase` tinyint NOT NULL DEFAULT 0 COMMENT '1=风采详情隐藏' AFTER `images`;

CREATE TABLE IF NOT EXISTS `player_showcase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `player_id` bigint NOT NULL COMMENT '绑定打手',
  `cover_url` varchar(512) NOT NULL DEFAULT '' COMMENT '运营封面，空则用头像',
  `tagline` varchar(32) NOT NULL DEFAULT '' COMMENT '一句话标签',
  `bio` varchar(512) NOT NULL DEFAULT '' COMMENT '运营简介',
  `display_rating` decimal(3,2) NULL COMMENT '风采展示评分，空则使用真实值',
  `display_completed_orders` int NULL COMMENT '风采展示完成单，空则使用真实值',
  `display_complete_rate` decimal(5,2) NULL COMMENT '风采展示完成率，空则使用真实值',
  `selected_images` varchar(4096) NOT NULL DEFAULT '' COMMENT '运营勾选的高光URL逗号分隔',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '越小越靠前',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0下架 1上架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_player_id` (`player_id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打手风采上墙卡';

-- 已执行过旧版建表 SQL 的存量数据库，单独执行以下迁移：
-- ALTER TABLE `player_showcase`
--   ADD COLUMN `display_rating` decimal(3,2) NULL COMMENT '风采展示评分，空则使用真实值' AFTER `bio`,
--   ADD COLUMN `display_completed_orders` int NULL COMMENT '风采展示完成单，空则使用真实值' AFTER `display_rating`,
--   ADD COLUMN `display_complete_rate` decimal(5,2) NULL COMMENT '风采展示完成率，空则使用真实值' AFTER `display_completed_orders`;
