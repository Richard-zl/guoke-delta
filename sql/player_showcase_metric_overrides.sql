-- 存量数据库升级：打手风采展示指标覆盖
ALTER TABLE `player_showcase`
  ADD COLUMN `display_rating` decimal(3,2) NULL COMMENT '风采展示评分，空则使用真实值' AFTER `bio`,
  ADD COLUMN `display_completed_orders` int NULL COMMENT '风采展示完成单，空则使用真实值' AFTER `display_rating`,
  ADD COLUMN `display_complete_rate` decimal(5,2) NULL COMMENT '风采展示完成率，空则使用真实值' AFTER `display_completed_orders`;
