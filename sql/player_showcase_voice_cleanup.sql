-- 清理无法在 iOS 播放的历史语音介绍。
-- 背景：上传接口曾按小程序临时文件名取扩展名，而微信开发者工具录音的实际内容
-- 是 WebM 却被命名为 .wav，iOS 真机播放时报 INNERERRCODE:-11800。
-- 现在上传接口已按文件真实内容命名，但存量脏数据仍需清掉并让打手重录。

-- 1. 先确认受影响的打手（.wav 均由开发者工具录制，真机录制的是 .mp3）
SELECT id, nickname, intro_voice_url, intro_voice_seconds
FROM `player`
WHERE intro_voice_url LIKE '%.wav';

-- 2. 清空这些语音，打手下次进入「我的风采」会看到未录音状态
UPDATE `player`
SET intro_voice_url = NULL, intro_voice_seconds = NULL
WHERE intro_voice_url LIKE '%.wav';

-- 3. 语音是上架硬条件，已上墙且刚被清空语音的打手需要下架，待重录后由运营重新上架
UPDATE `player_showcase` ps
JOIN `player` p ON p.id = ps.player_id
SET ps.status = 0
WHERE ps.status = 1 AND (p.intro_voice_url IS NULL OR p.intro_voice_url = '');
