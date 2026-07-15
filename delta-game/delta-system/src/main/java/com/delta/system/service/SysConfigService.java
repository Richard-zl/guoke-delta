package com.delta.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.system.entity.SysConfig;

public interface SysConfigService extends IService<SysConfig> {
    /** 根据 key 获取配置值（带 Redis 缓存） */
    String getConfigValue(String key);

    /** 获取配置值，不存在返回默认值 */
    String getConfigValue(String key, String defaultValue);
}
