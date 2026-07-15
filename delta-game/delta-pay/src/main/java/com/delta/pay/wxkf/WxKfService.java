package com.delta.pay.wxkf;

import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.exception.BusinessException;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.pay.domain.PayTokenPayload;
import com.delta.pay.service.PayTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信客服（企业微信「微信客服」应用）能力封装：access_token 缓存、sync_msg 拉取新消息、
 * send_msg_on_event / send_msg 主动发消息。核心业务：用户进入会话时校验 scene_param(payToken)
 * 并自动推送 H5 支付链接（Spec §5.3）。
 */
@Slf4j
@Service
public class WxKfService {

    private static final String ACCESS_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String SYNC_MSG_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/sync_msg";
    private static final String SEND_MSG_ON_EVENT_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg_on_event";
    private static final String SEND_MSG_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg";
    private static final String ACCESS_TOKEN_REDIS_KEY = "pay:kf:access_token";
    private static final String CURSOR_REDIS_KEY_PREFIX = "pay:kf:cursor:";
    private static final long EXPIRE_SAFETY_MARGIN_SECONDS = 200;
    private static final int SYNC_MSG_LIMIT = 1000;
    private static final int SYNC_LOOP_GUARD = 20;

    private final WxKfProperties properties;
    private final PayTokenService payTokenService;
    private final OrderService orderService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${pay.kf.h5-pay-base-url:}")
    private String h5PayBaseUrl;

    public WxKfService(WxKfProperties properties, PayTokenService payTokenService, OrderService orderService,
                        StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.properties = properties;
        this.payTokenService = payTokenService;
        this.orderService = orderService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一次回调事件通知：拉取该客服账号自上次 cursor 之后的全部新消息，
     * 对 enter_session 事件自动校验 payToken 并推送支付链接。失败仅记录日志，不向上抛出
     * （回调必须始终返回 success，避免微信重试风暴）。
     */
    public void syncAndDispatch(String kfToken, String openKfId) {
        if (isBlank(properties.getSecret())) {
            log.error("wx.kf.secret未配置，无法同步客服消息 openKfId={}", openKfId);
            return;
        }
        String cursorKey = CURSOR_REDIS_KEY_PREFIX + openKfId;
        String cursor = redis.opsForValue().get(cursorKey);
        if (cursor == null) {
            cursor = "";
        }
        try {
            boolean hasMore = true;
            int guard = 0;
            while (hasMore && guard < SYNC_LOOP_GUARD) {
                guard++;
                Map<String, Object> body = new HashMap<>();
                body.put("cursor", cursor);
                body.put("token", kfToken);
                body.put("limit", SYNC_MSG_LIMIT);
                body.put("open_kfid", openKfId);
                JsonNode resp = post(SYNC_MSG_URL + "?access_token=" + getAccessToken(), body);
                checkErrcode(resp, "kf/sync_msg");

                JsonNode msgList = resp.path("msg_list");
                if (msgList.isArray()) {
                    for (JsonNode msg : msgList) {
                        dispatchMessage(msg);
                    }
                }
                cursor = resp.path("next_cursor").asText(cursor);
                redis.opsForValue().set(cursorKey, cursor);
                hasMore = resp.path("has_more").asInt(0) == 1;
            }
        } catch (Exception e) {
            log.error("同步客服消息失败 openKfId={}", openKfId, e);
        }
    }

    private void dispatchMessage(JsonNode msg) {
        if (!"event".equals(msg.path("msgtype").asText())) {
            return;
        }
        JsonNode event = msg.path("event");
        if (!"enter_session".equals(event.path("event_type").asText())) {
            return;
        }
        String eventOpenKfId = event.path("open_kfid").asText(null);
        String externalUserId = event.path("external_userid").asText(null);
        String sceneParam = event.path("scene_param").asText(null);
        String welcomeCode = event.path("welcome_code").asText(null);
        handleEnterSession(eventOpenKfId, externalUserId, sceneParam, welcomeCode);
    }

    /** 处理进入会话事件：校验 payToken → 订单待支付 → 自动推送 H5 支付链接；否则文案引导。 */
    void handleEnterSession(String openKfId, String externalUserId, String sceneParam, String welcomeCode) {
        if (isBlank(sceneParam)) {
            deliver(welcomeCode, openKfId, externalUserId, textMsg("请回到小程序重新发起支付"));
            return;
        }

        PayTokenPayload payload;
        try {
            payload = payTokenService.verify(sceneParam);
        } catch (BusinessException e) {
            deliver(welcomeCode, openKfId, externalUserId, textMsg("支付链接已失效，请回到小程序重新发起"));
            return;
        }

        Order order = orderService.getById(payload.orderId());
        if (order == null) {
            deliver(welcomeCode, openKfId, externalUserId, textMsg("请回到小程序重新发起支付"));
            return;
        }
        if (OrderStatusEnum.PAID.name().equals(order.getStatus())) {
            deliver(welcomeCode, openKfId, externalUserId, textMsg("该订单已支付，无需重复支付"));
            return;
        }
        if (!OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus())) {
            deliver(welcomeCode, openKfId, externalUserId, textMsg("订单状态已变更，请回到小程序查看"));
            return;
        }
        if (isBlank(h5PayBaseUrl)) {
            log.error("pay.kf.h5-pay-base-url未配置，无法生成支付链接 orderId={}", order.getId());
            deliver(welcomeCode, openKfId, externalUserId, textMsg("支付链接生成失败，请稍后重试或联系客服"));
            return;
        }

        String link = buildPayLink(sceneParam);
        deliver(welcomeCode, openKfId, externalUserId, linkMsg("订单支付", buildDescription(order), link));
    }

    /** 首选 send_msg_on_event（welcome_code 时间窗约20s）；失败或无 code 时降级 send_msg（需48h会话窗口）。 */
    private void deliver(String welcomeCode, String openKfId, String externalUserId, Map<String, Object> msgPayload) {
        if (!isBlank(welcomeCode)) {
            try {
                sendMsgOnEvent(welcomeCode, msgPayload);
                return;
            } catch (Exception e) {
                log.warn("send_msg_on_event失败，尝试降级send_msg openKfId={}, reason={}", openKfId, e.getMessage());
            }
        }
        if (isBlank(openKfId) || isBlank(externalUserId)) {
            log.warn("缺少open_kfid/external_userid，无法降级调用send_msg");
            return;
        }
        try {
            sendMsg(externalUserId, openKfId, msgPayload);
        } catch (Exception e) {
            log.error("send_msg降级发送失败 openKfId={}", openKfId, e);
        }
    }

    /** 通过 welcome_code 在进入会话事件时间窗内主动发消息 */
    public void sendMsgOnEvent(String welcomeCode, Map<String, Object> msgPayload) {
        Map<String, Object> body = new HashMap<>(msgPayload);
        body.put("code", welcomeCode);
        JsonNode resp = post(SEND_MSG_ON_EVENT_URL + "?access_token=" + getAccessToken(), body);
        checkErrcode(resp, "kf/send_msg_on_event");
    }

    /** 48小时会话窗口内主动发消息（降级方案） */
    public void sendMsg(String touser, String openKfId, Map<String, Object> msgPayload) {
        Map<String, Object> body = new HashMap<>(msgPayload);
        body.put("touser", touser);
        body.put("open_kfid", openKfId);
        JsonNode resp = post(SEND_MSG_URL + "?access_token=" + getAccessToken(), body);
        checkErrcode(resp, "kf/send_msg");
    }

    private Map<String, Object> textMsg(String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("msgtype", "text");
        Map<String, Object> text = new HashMap<>();
        text.put("content", content);
        map.put("text", text);
        return map;
    }

    private Map<String, Object> linkMsg(String title, String desc, String url) {
        Map<String, Object> map = new HashMap<>();
        map.put("msgtype", "link");
        Map<String, Object> link = new HashMap<>();
        link.put("title", title);
        link.put("desc", desc);
        link.put("url", url);
        map.put("link", link);
        return map;
    }

    private String buildDescription(Order order) {
        if (order.getProductName() != null && !order.getProductName().isEmpty()) {
            return order.getProductName();
        }
        return "点击完成支付";
    }

    private String buildPayLink(String payToken) {
        String encoded = URLEncoder.encode(payToken, StandardCharsets.UTF_8);
        String separator = h5PayBaseUrl.contains("?") ? "&" : "?";
        return h5PayBaseUrl + separator + "token=" + encoded;
    }

    private String getAccessToken() {
        String cached = redis.opsForValue().get(ACCESS_TOKEN_REDIS_KEY);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        if (isBlank(properties.getCorpId()) || isBlank(properties.getSecret())) {
            throw new BusinessException("企业微信corp-id/secret未配置，无法获取access_token");
        }
        String url = ACCESS_TOKEN_URL + "?corpid=" + properties.getCorpId() + "&corpsecret=" + properties.getSecret();
        JsonNode resp = get(url);
        checkErrcode(resp, "gettoken");
        String accessToken = resp.path("access_token").asText(null);
        long expiresIn = resp.path("expires_in").asLong(7200);
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("获取企业微信access_token失败：响应缺少access_token");
        }
        long ttl = Math.max(60, expiresIn - EXPIRE_SAFETY_MARGIN_SECONDS);
        redis.opsForValue().set(ACCESS_TOKEN_REDIS_KEY, accessToken, Duration.ofSeconds(ttl));
        return accessToken;
    }

    private void checkErrcode(JsonNode resp, String api) {
        int errcode = resp.path("errcode").asInt(0);
        if (errcode != 0) {
            String errmsg = resp.path("errmsg").asText("");
            log.error("企业微信接口调用失败 api={}, errcode={}, errmsg={}", api, errcode, errmsg);
            throw new BusinessException(api + "调用失败: " + errmsg);
        }
    }

    private JsonNode get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new BusinessException("请求企业微信接口异常: " + url);
        }
    }

    private JsonNode post(String url, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            throw new BusinessException("请求企业微信接口异常: " + url);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
