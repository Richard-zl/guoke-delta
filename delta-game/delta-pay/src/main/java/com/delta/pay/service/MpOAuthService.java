package com.delta.pay.service;

import com.delta.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 服务号（公众号）网页授权 code → openid。
 * 独立于 {@link com.delta.pay.config.WxPayConfiguration}（该 Bean 受 wx.pay.enabled 条件约束，
 * 服务号 H5 授权不应依赖微信支付是否启用），appid/secret 直接来自 wx.mp.* 配置。
 */
@Slf4j
@Service
public class MpOAuthService {

    private static final String OAUTH_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;

    @Value("${wx.mp.appid:}")
    private String mpAppId;

    @Value("${wx.mp.secret:}")
    private String mpSecret;

    public MpOAuthService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 用网页授权 code 换取服务号 openid（snsapi_base，静默授权）。
     */
    public String codeToOpenid(String code) {
        if (mpAppId == null || mpAppId.isBlank() || mpSecret == null || mpSecret.isBlank()) {
            throw new BusinessException("服务号未配置，无法完成授权");
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException("授权code不能为空");
        }
        String url = OAUTH_URL
                + "?appid=" + mpAppId
                + "&secret=" + mpSecret
                + "&code=" + code
                + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode node = objectMapper.readTree(response.body());
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                log.error("服务号授权失败, errcode={}, errmsg={}", node.get("errcode").asText(), node.path("errmsg").asText());
                throw new BusinessException("服务号授权失败: " + node.path("errmsg").asText());
            }
            String openid = node.path("openid").asText(null);
            if (openid == null || openid.isBlank()) {
                throw new BusinessException("服务号授权失败: 未返回openid");
            }
            return openid;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("服务号授权请求异常, code={}", code, e);
            throw new BusinessException("服务号授权请求异常，请稍后重试");
        }
    }
}
