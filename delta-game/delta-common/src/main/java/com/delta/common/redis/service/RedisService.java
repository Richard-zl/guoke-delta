package com.delta.common.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {
    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) { redisTemplate.opsForValue().set(key, value); }
    public void set(String key, Object value, long timeout, TimeUnit unit) { redisTemplate.opsForValue().set(key, value, timeout, unit); }
    public Object get(String key) { return redisTemplate.opsForValue().get(key); }
    public Boolean delete(String key) { return redisTemplate.delete(key); }
    public Boolean hasKey(String key) { return redisTemplate.hasKey(key); }
    public Long increment(String key) { return redisTemplate.opsForValue().increment(key); }
    public void expire(String key, long timeout, TimeUnit unit) { redisTemplate.expire(key, timeout, unit); }

    // Aliases used by SysConfigServiceImpl
    @SuppressWarnings("unchecked")
    public <T> T getCacheObject(String key) { return (T) redisTemplate.opsForValue().get(key); }
    public <T> void setCacheObject(String key, T value, Long timeout, TimeUnit unit) { redisTemplate.opsForValue().set(key, value, timeout, unit); }
    public void deleteObject(String key) { redisTemplate.delete(key); }

    public boolean tryLock(String key, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "1", timeout, unit);
        return Boolean.TRUE.equals(result);
    }
    public void unlock(String key) { redisTemplate.delete(key); }
}
