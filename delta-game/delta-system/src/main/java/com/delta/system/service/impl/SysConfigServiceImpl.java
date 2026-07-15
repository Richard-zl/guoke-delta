package com.delta.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.redis.service.RedisService;
import com.delta.system.entity.SysConfig;
import com.delta.system.mapper.SysConfigMapper;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {
    private static final String CACHE_PREFIX = "sys_config:";
    private final RedisService redisService;

    @Override
    public String getConfigValue(String key) {
        String cached = redisService.getCacheObject(CACHE_PREFIX + key);
        if (cached != null) return cached;
        SysConfig config = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config != null) {
            redisService.setCacheObject(CACHE_PREFIX + key, config.getConfigValue(), 1L, TimeUnit.HOURS);
            return config.getConfigValue();
        }
        return null;
    }

    @Override
    public String getConfigValue(String key, String defaultValue) {
        String val = getConfigValue(key);
        return val != null ? val : defaultValue;
    }

    @Override
    public boolean updateById(SysConfig entity) {
        // 先查出 configKey 再清缓存（前端可能只传 id + configValue）
        if (entity.getConfigKey() == null && entity.getId() != null) {
            SysConfig old = getById(entity.getId());
            if (old != null) entity.setConfigKey(old.getConfigKey());
        }
        boolean ok = super.updateById(entity);
        if (ok && entity.getConfigKey() != null) {
            redisService.deleteObject(CACHE_PREFIX + entity.getConfigKey());
        }
        return ok;
    }

    @Override
    public boolean updateBatchById(java.util.Collection<SysConfig> entityList) {
        boolean ok = super.updateBatchById(entityList);
        if (ok) {
            for (SysConfig entity : entityList) {
                if (entity.getConfigKey() == null && entity.getId() != null) {
                    SysConfig old = getById(entity.getId());
                    if (old != null) entity.setConfigKey(old.getConfigKey());
                }
                if (entity.getConfigKey() != null) {
                    redisService.deleteObject(CACHE_PREFIX + entity.getConfigKey());
                }
            }
        }
        return ok;
    }
}
