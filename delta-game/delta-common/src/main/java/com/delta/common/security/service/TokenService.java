package com.delta.common.security.service;

import com.delta.common.constant.CacheConstants;
import com.delta.common.redis.service.RedisService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenService {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration:86400000}")
    private long expiration;
    @Autowired
    private RedisService redisService;

    public String createToken(Long userId, com.delta.common.enums.UserTypeEnum userTypeEnum) {
        return createToken(userId, userTypeEnum.getCode());
    }

    public String createToken(Long userId, String userType) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("userType", userType);
        claims.put("uuid", uuid);

        String token = Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey())
                .compact();

        // 存入Redis
        Map<String, Object> userContext = new HashMap<>();
        userContext.put("userId", userId);
        userContext.put("userType", userType);
        redisService.set(CacheConstants.LOGIN_TOKEN_KEY + uuid, userContext,
                CacheConstants.TOKEN_EXPIRE, TimeUnit.MINUTES);
        return token;
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            log.debug("Token解析失败: {}", e.getMessage());
            return null;
        }
    }

    public String getToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserContext(String uuid) {
        Object obj = redisService.get(CacheConstants.LOGIN_TOKEN_KEY + uuid);
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }

    public void removeToken(String uuid) {
        redisService.delete(CacheConstants.LOGIN_TOKEN_KEY + uuid);
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
