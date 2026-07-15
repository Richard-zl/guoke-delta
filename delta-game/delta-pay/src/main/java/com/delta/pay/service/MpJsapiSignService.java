package com.delta.pay.service;

import com.delta.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务号 JSSDK（wx.config）签名服务：获取/缓存基础 access_token 与 jsapi_ticket，
 * 按微信官方算法对目标页面 url 签名。appId 取自 wx.mp.appid。
 */
@Slf4j
@Service
public class MpJsapiSignService {

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String JSAPI_TICKET_URL = "https://api.weixin.qq.com/cgi-bin/ticket/getticket";
    private static final String ACCESS_TOKEN_REDIS_KEY = "pay:mp:access_token";
    private static final String JSAPI_TICKET_REDIS_KEY = "pay:mp:jsapi_ticket";
    /** 微信声明有效期通常为7200秒，提前留出安全余量避免边界过期 */
    private static final long EXPIRE_SAFETY_MARGIN_SECONDS = 200;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    @Value("${wx.mp.appid:}")
    private String mpAppId;

    @Value("${wx.mp.secret:}")
    private String mpSecret;

    public MpJsapiSignService(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    /**
     * 对目标页面 url 生成 wx.config 所需签名参数。
     */
    public Map<String, String> sign(String url) {
        if (mpAppId == null || mpAppId.isBlank() || mpSecret == null || mpSecret.isBlank()) {
            throw new BusinessException("服务号未配置，无法生成JSSDK签名");
        }
        if (url == null || url.isBlank()) {
            throw new BusinessException("url不能为空");
        }
        String ticket = getJsapiTicket();
        String nonceStr = randomNonceStr();
        long timestamp = Instant.now().getEpochSecond();

        String raw = "jsapi_ticket=" + ticket
                + "&noncestr=" + nonceStr
                + "&timestamp=" + timestamp
                + "&url=" + url;
        String signature = sha1Hex(raw);

        Map<String, String> result = new HashMap<>();
        result.put("appId", mpAppId);
        result.put("timestamp", String.valueOf(timestamp));
        result.put("nonceStr", nonceStr);
        result.put("signature", signature);
        return result;
    }

    private String getJsapiTicket() {
        String cached = redis.opsForValue().get(JSAPI_TICKET_REDIS_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String accessToken = getAccessToken();
        String url = JSAPI_TICKET_URL + "?access_token=" + accessToken + "&type=jsapi";
        JsonNode node = get(url);
        if (node.has("errcode") && node.get("errcode").asInt() != 0) {
            log.error("获取jsapi_ticket失败, errcode={}, errmsg={}", node.get("errcode").asText(), node.path("errmsg").asText());
            throw new BusinessException("获取jsapi_ticket失败: " + node.path("errmsg").asText());
        }
        String ticket = node.path("ticket").asText(null);
        long expiresIn = node.path("expires_in").asLong(7200);
        if (ticket == null || ticket.isBlank()) {
            throw new BusinessException("获取jsapi_ticket失败: 响应缺少ticket");
        }
        long ttl = Math.max(60, expiresIn - EXPIRE_SAFETY_MARGIN_SECONDS);
        redis.opsForValue().set(JSAPI_TICKET_REDIS_KEY, ticket, Duration.ofSeconds(ttl));
        return ticket;
    }

    private String getAccessToken() {
        String cached = redis.opsForValue().get(ACCESS_TOKEN_REDIS_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String url = ACCESS_TOKEN_URL + "?grant_type=client_credential&appid=" + mpAppId + "&secret=" + mpSecret;
        JsonNode node = get(url);
        if (node.has("errcode") && node.get("errcode").asInt() != 0) {
            log.error("获取服务号access_token失败, errcode={}, errmsg={}", node.get("errcode").asText(), node.path("errmsg").asText());
            throw new BusinessException("获取服务号access_token失败: " + node.path("errmsg").asText());
        }
        String accessToken = node.path("access_token").asText(null);
        long expiresIn = node.path("expires_in").asLong(7200);
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("获取服务号access_token失败: 响应缺少access_token");
        }
        long ttl = Math.max(60, expiresIn - EXPIRE_SAFETY_MARGIN_SECONDS);
        redis.opsForValue().set(ACCESS_TOKEN_REDIS_KEY, accessToken, Duration.ofSeconds(ttl));
        return accessToken;
    }

    private JsonNode get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return objectMapper.readTree(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("请求微信接口异常, url={}", url, e);
            throw new BusinessException("请求微信接口异常，请稍后重试");
        }
    }

    private String randomNonceStr() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < 16; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String sha1Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException("签名计算失败");
        }
    }
}
