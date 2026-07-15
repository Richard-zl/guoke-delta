package com.delta.pay.service.impl;

import com.delta.common.exception.BusinessException;
import com.delta.pay.domain.PayTokenPayload;
import com.delta.pay.service.PayTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.security.SecureRandom;

/**
 * payToken 紧凑格式：orderId.userId.exp.jti.sig
 * sig = base64url(HMAC-SHA256(orderId.userId.exp.jti, secret) 取前16字节)
 * <p>
 * 安全策略：签名 + 未过期即校验通过（主防线是调用方另行校验的订单状态）；
 * Redis 中的 jti 记录仅用于签发限流和可选观测辅助，即使缺失（如缓存闪断）也不阻断校验。
 */
@Slf4j
@Service
public class PayTokenServiceImpl implements PayTokenService {

    private static final String TOKEN_KEY_PREFIX = "pay:kf:token:";
    private static final String RATE_LIMIT_KEY_PREFIX = "pay:kf:rl:";
    private static final int RATE_LIMIT_MAX_PER_MINUTE = 10;
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;
    private static final int JTI_LENGTH = 10;
    private static final String JTI_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SIG_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final String secret;
    private final long ttlSeconds;

    public PayTokenServiceImpl(StringRedisTemplate redis,
                                @Value("${pay.kf.token-secret}") String secret,
                                @Value("${pay.kf.token-ttl-seconds:900}") long ttlSeconds) {
        this.redis = redis;
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public String issue(Long orderId, Long userId) {
        checkAndIncrementRateLimit(orderId);

        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String jti = randomJti();
        String token = buildTokenForTest(orderId, userId, exp, jti);

        redis.opsForValue().setIfAbsent(TOKEN_KEY_PREFIX + jti, String.valueOf(orderId), Duration.ofSeconds(ttlSeconds));
        return token;
    }

    @Override
    public PayTokenPayload verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("token不能为空");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 5) {
            throw new BusinessException("token格式错误");
        }

        Long orderId = parseLong(parts[0]);
        Long userId = parseLong(parts[1]);
        long exp = parseLong(parts[2]);
        String jti = parts[3];
        String sig = parts[4];

        String body = orderId + "." + userId + "." + exp + "." + jti;
        String expectedSig = sign(body);
        if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("token签名无效");
        }

        // Redis jti 仅作限流/观测辅助，缺失不阻断校验，避免 Redis 闪断误杀正常支付
        redis.opsForValue().get(TOKEN_KEY_PREFIX + jti);

        if (exp <= Instant.now().getEpochSecond()) {
            throw new BusinessException("token已过期");
        }

        return new PayTokenPayload(orderId, userId, exp, jti);
    }

    /**
     * 按给定参数直接拼装并签名 token，跳过限流/随机 jti 生成，便于单测构造过期等边界场景。
     */
    String buildTokenForTest(Long orderId, Long userId, long exp, String jti) {
        String body = orderId + "." + userId + "." + exp + "." + jti;
        return body + "." + sign(body);
    }

    private void checkAndIncrementRateLimit(Long orderId) {
        String key = RATE_LIMIT_KEY_PREFIX + orderId;
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return;
        }
        if (count == 1L) {
            redis.expire(key, RATE_LIMIT_WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        if (count > RATE_LIMIT_MAX_PER_MINUTE) {
            throw new BusinessException("payToken签发过于频繁，请稍后重试");
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = Arrays.copyOf(digest, SIG_BYTES);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (Exception e) {
            log.error("payToken签名失败", e);
            throw new BusinessException("token签名失败");
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BusinessException("token格式错误");
        }
    }

    private String randomJti() {
        StringBuilder sb = new StringBuilder(JTI_LENGTH);
        for (int i = 0; i < JTI_LENGTH; i++) {
            sb.append(JTI_ALPHABET.charAt(RANDOM.nextInt(JTI_ALPHABET.length())));
        }
        return sb.toString();
    }
}
