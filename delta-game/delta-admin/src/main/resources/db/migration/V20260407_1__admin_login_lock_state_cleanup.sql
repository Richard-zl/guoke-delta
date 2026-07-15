UPDATE `admin`
SET `login_fail_count` = 0,
    `lock_time` = NULL
WHERE `lock_time` IS NOT NULL
  AND `lock_time` < NOW();

UPDATE `admin`
SET `login_fail_count` = 0
WHERE `lock_time` IS NULL
  AND `login_fail_count` <> 0;
