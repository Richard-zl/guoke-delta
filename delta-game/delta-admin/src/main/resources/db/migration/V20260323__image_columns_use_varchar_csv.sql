ALTER TABLE `complaint`
    MODIFY COLUMN `images` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '证据图片URL，多个以逗号分隔',
    MODIFY COLUMN `appeal_images` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '申诉证据图片，多个以逗号分隔';

UPDATE `complaint`
SET `images` = NULLIF(TRIM(BOTH ',' FROM TRIM(BOTH '"' FROM REPLACE(REPLACE(REPLACE(REPLACE(`images`, '[', ''), ']', ''), '"', ''), ', ', ','))), '')
WHERE `images` IS NOT NULL;

UPDATE `complaint`
SET `appeal_images` = NULLIF(TRIM(BOTH ',' FROM TRIM(BOTH '"' FROM REPLACE(REPLACE(REPLACE(REPLACE(`appeal_images`, '[', ''), ']', ''), '"', ''), ', ', ','))), '')
WHERE `appeal_images` IS NOT NULL;

ALTER TABLE `order_progress`
    MODIFY COLUMN `images` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '截图URL，多个以逗号分隔';

UPDATE `order_progress`
SET `images` = NULLIF(TRIM(BOTH ',' FROM TRIM(BOTH '"' FROM REPLACE(REPLACE(REPLACE(REPLACE(`images`, '[', ''), ']', ''), '"', ''), ', ', ','))), '')
WHERE `images` IS NOT NULL;

ALTER TABLE `player`
    MODIFY COLUMN `proof_images` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '技能证明截图，多个以逗号分隔';

UPDATE `player`
SET `proof_images` = NULLIF(TRIM(BOTH ',' FROM TRIM(BOTH '"' FROM REPLACE(REPLACE(REPLACE(REPLACE(`proof_images`, '[', ''), ']', ''), '"', ''), ', ', ','))), '')
WHERE `proof_images` IS NOT NULL;

ALTER TABLE `product`
    MODIFY COLUMN `images` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '详情图URL，多个以逗号分隔';

UPDATE `product`
SET `images` = NULLIF(TRIM(BOTH ',' FROM TRIM(BOTH '"' FROM REPLACE(REPLACE(REPLACE(REPLACE(`images`, '[', ''), ']', ''), '"', ''), ', ', ','))), '')
WHERE `images` IS NOT NULL;

ALTER TABLE `review`
    MODIFY COLUMN `images` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '评价图片URL，多个以逗号分隔';

UPDATE `review`
SET `images` = NULLIF(TRIM(BOTH ',' FROM TRIM(BOTH '"' FROM REPLACE(REPLACE(REPLACE(REPLACE(`images`, '[', ''), ']', ''), '"', ''), ', ', ','))), '')
WHERE `images` IS NOT NULL;
