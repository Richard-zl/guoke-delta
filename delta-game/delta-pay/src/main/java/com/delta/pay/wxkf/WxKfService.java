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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信客服能力封装（方案 A：同客服号按入口分流）。
 * <ul>
 *   <li>支付入口 scene=pay 且订单待支付：进线尽量立刻发「蓝字」支付菜单（msgmenu）；发完分配人工。
 *       若当前人工/排队则先结束会话再用结束语发送；state=4 且无 welcome_code 时只能暂存，客户发言后补发并分配。
 *       底线：暂存 + 客户发言补发路径必须始终可用，不得比「回一句就能发出」更差。</li>
 *   <li>咨询入口：不发支付，客户发言后分配人工。</li>
 * </ul>
 */
@Slf4j
@Service
public class WxKfService {

    private static final DateTimeFormatter PAY_MSG_TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private static final String ACCESS_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String SYNC_MSG_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/sync_msg";
    private static final String SEND_MSG_ON_EVENT_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg_on_event";
    private static final String SEND_MSG_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg";
    /** 获取带 scene 的客服链接；仅此类链接支持拼接 scene_param 并在 enter_session 回传 */
    private static final String ADD_CONTACT_WAY_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/add_contact_way";
    /** 变更会话状态 / 获取接待人员：API 管会话后需自行分配到人工 */
    private static final String SERVICE_STATE_TRANS_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/service_state/trans";
    private static final String SERVICE_STATE_GET_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/service_state/get";
    private static final String SERVICER_LIST_URL = "https://qyapi.weixin.qq.com/cgi-bin/kf/servicer/list";
    /** 1=智能助手接待（send_msg 前短暂使用） */
    private static final int SERVICE_STATE_AI = 1;
    /** 2=待接入池排队 */
    private static final int SERVICE_STATE_WAITING_POOL = 2;
    /** 3=由人工接待（此状态下 API send_msg 会报 95018） */
    private static final int SERVICE_STATE_HUMAN = 3;
    /** 4=已结束/未开始；结束会话返回的 msg_code 可发文本/菜单 */
    private static final int SERVICE_STATE_ENDED = 4;
    /** 接待人员 status：0=接待中，1=停止接待 */
    private static final int SERVICER_STATUS_ONLINE = 0;
    private static final String ACCESS_TOKEN_REDIS_KEY = "pay:kf:access_token";
    private static final String CURSOR_REDIS_KEY_PREFIX = "pay:kf:cursor:";
    private static final String CONTACT_WAY_REDIS_KEY_PREFIX = "pay:kf:contact_way:";
    private static final String SERVICER_RR_REDIS_KEY_PREFIX = "pay:kf:servicer_rr:";
    /** 支付引导一时发不出时暂存，客户发言后再推（JSON：url/title/desc） */
    private static final String PENDING_PAY_MSG_KEY_PREFIX = "pay:kf:pending_msg:";
    /** add_contact_way 的 scene，与支付入口对应；回调里用此值识别支付场景 */
    public static final String PAY_SCENE = "pay";
    private static final long EXPIRE_SAFETY_MARGIN_SECONDS = 200;
    private static final long PENDING_PAY_MSG_TTL_SECONDS = 900;
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
            int total = 0;
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
                    total += msgList.size();
                    for (JsonNode msg : msgList) {
                        dispatchMessage(msg);
                    }
                }
                cursor = resp.path("next_cursor").asText(cursor);
                redis.opsForValue().set(cursorKey, cursor);
                hasMore = resp.path("has_more").asInt(0) == 1;
            }
            log.info("客服消息同步完成 openKfId={}, pulled={}", openKfId, total);
        } catch (Exception e) {
            log.error("同步客服消息失败 openKfId={}", openKfId, e);
        }
    }

    private void dispatchMessage(JsonNode msg) {
        String msgType = msg.path("msgtype").asText();
        // origin: 3=微信客户 4=系统 5=接待人员
        int origin = msg.path("origin").asInt(0);

        if ("event".equals(msgType)) {
            JsonNode event = msg.path("event");
            if (!"enter_session".equals(event.path("event_type").asText())) {
                return;
            }
            String eventOpenKfId = event.path("open_kfid").asText(null);
            String externalUserId = event.path("external_userid").asText(null);
            String scene = event.path("scene").asText(null);
            String sceneParam = event.path("scene_param").asText(null);
            String welcomeCode = event.path("welcome_code").asText(null);
            log.info("enter_session raw scene={}, sceneParamLen={}, hasWelcomeCode={}, event={}",
                    scene,
                    sceneParam == null ? 0 : sceneParam.length(),
                    !isBlank(welcomeCode),
                    event);
            handleEnterSession(eventOpenKfId, externalUserId, scene, sceneParam, welcomeCode);
            return;
        }

        // 开启 API 管会话后需自行分配；客户发言后优先补发暂存的支付链接，再指派人工
        if (origin == 3) {
            String openKfId = msg.path("open_kfid").asText(null);
            String externalUserId = msg.path("external_userid").asText(null);
            log.info("收到客户消息 msgtype={}, openKfId={}, externalUserId={}", msgType, openKfId, externalUserId);
            flushPendingPayMessage(openKfId, externalUserId);
            enqueueForHumanIfNeeded(openKfId, externalUserId);
        }
    }

    /**
     * 通过 add_contact_way(scene=pay) 拿到可透传 scene_param 的客服链接，并拼上 payToken。
     * 后台手工复制的客服链接通常没有 enc_scene，拼 scene_param 也不会回传。
     */
    public String buildPayServiceUrl(String payToken) {
        if (isBlank(payToken)) {
            throw new BusinessException("payToken不能为空");
        }
        String base = getContactWayUrl(PAY_SCENE);
        String encoded = URLEncoder.encode(payToken, StandardCharsets.UTF_8);
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "scene_param=" + encoded;
    }

    /** 获取（并缓存）带 scene 的客服账号链接 */
    public String getContactWayUrl(String scene) {
        if (isBlank(properties.getOpenKfid())) {
            throw new BusinessException("未配置 wx.kf.open-kfid，无法生成支付客服链接");
        }
        if (isBlank(scene)) {
            throw new BusinessException("scene不能为空");
        }
        String cacheKey = CONTACT_WAY_REDIS_KEY_PREFIX + scene;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("open_kfid", properties.getOpenKfid());
        body.put("scene", scene);
        JsonNode resp = post(ADD_CONTACT_WAY_URL + "?access_token=" + getAccessToken(), body);
        checkErrcode(resp, "kf/add_contact_way");
        String url = resp.path("url").asText(null);
        if (isBlank(url)) {
            throw new BusinessException("kf/add_contact_way未返回url");
        }
        // 链接本身长期有效，缓存避免频繁调用
        redis.opsForValue().set(cacheKey, url);
        log.info("已生成客服联系链接 scene={}, url={}", scene, url);
        return url;
    }

    /** 处理进入会话事件：校验 payToken → 订单待支付 → 自动推送 H5 支付链接；否则文案引导。 */
    void handleEnterSession(String openKfId, String externalUserId, String scene,
                            String sceneParam, String welcomeCode) {
        // welcome_code 仅在「48h 内未收过欢迎语且未发过消息」时下发；send_msg_on_event 只支持 text/msgmenu
        log.info("enter_session openKfId={}, externalUserId={}, scene={}, hasSceneParam={}, hasWelcomeCode={}",
                openKfId, externalUserId, scene, !isBlank(sceneParam), !isBlank(welcomeCode));

        if (isBlank(sceneParam)) {
            // 普通咨询入口不要刷支付文案；仅支付 scene 且丢了 token 时提示
            if (PAY_SCENE.equals(scene)) {
                deliver(welcomeCode, openKfId, externalUserId, textMsg("请回到小程序重新发起支付"));
            } else {
                log.info("enter_session无scene_param且非pay场景，跳过支付自动回复");
                // 刚进会话多为 state=4，API 不能改状态；客户发消息后会再走 enqueue
                enqueueForHumanIfNeeded(openKfId, externalUserId);
            }
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
        // 产品策略：蓝字菜单进线即发；每次待支付进线都发；发完尽量分配人工；失败必暂存
        deliverPayGuide(welcomeCode, openKfId, externalUserId,
                "订单支付", buildPayMenuDesc(order), link);
    }

    /**
     * 推送支付蓝字菜单（msgmenu），并尽量分配人工。
     * <ul>
     *   <li>有 welcome_code：事件通道直接发</li>
     *   <li>state=0/1：send_msg 发菜单后转人工（不结束会话，避免下次进线落在 state=4）</li>
     *   <li>state=2/3：结束会话，用结束语 msg_code 发菜单（保证能发出）</li>
     *   <li>其余发不出：暂存，客户发言后补发（底线路径，不可削弱）</li>
     * </ul>
     */
    private void deliverPayGuide(String welcomeCode, String openKfId, String externalUserId,
                                 String title, String desc, String url) {
        if (isBlank(openKfId) || isBlank(externalUserId) || isBlank(url)) {
            return;
        }

        Map<String, Object> menuPayload = payMenuMsg(title, desc, url);
        boolean sent = false;
        boolean sessionEndedForSend = false;
        int state = getServiceState(openKfId, externalUserId);
        log.info("支付引导发送前会话状态 openKfId={}, service_state={}, hasWelcomeCode={}",
                openKfId, state, !isBlank(welcomeCode));

        // 1) 进线欢迎语通道（文本/菜单）
        if (!isBlank(welcomeCode)) {
            try {
                sendMsgOnEvent(welcomeCode, menuPayload);
                log.info("已通过welcome_code推送支付蓝字菜单 openKfId={}", openKfId);
                sent = true;
            } catch (Exception e) {
                log.warn("welcome_code发送失败，继续按会话状态降级 openKfId={}, reason={}", openKfId, e.getMessage());
            }
        }

        // 2) 智能助手 / 未处理：可 send_msg（成功后只转人工，绝不结束会话）
        if (!sent && (state == 0 || state == SERVICE_STATE_AI)) {
            sent = trySendMenuBySendMsg(externalUserId, openKfId, state, menuPayload);
        }

        // 3) 排队/人工：不能 send_msg，结束会话后用结束语发菜单（保证本次能发出）
        if (!sent && (state == SERVICE_STATE_WAITING_POOL || state == SERVICE_STATE_HUMAN)) {
            String endCode = changeServiceState(openKfId, externalUserId, SERVICE_STATE_ENDED, null,
                    "结束会话以发送支付链接");
            sent = sendByMsgCode(endCode, menuPayload, openKfId, "结束会话");
            sessionEndedForSend = sent;
        }

        if (sent) {
            clearPendingPayMessage(openKfId, externalUserId);
            if (sessionEndedForSend) {
                // 结束后 API 无法立刻分配；客户再发言仍会 flush(空)+enqueue
                log.info("支付菜单已发送(经结束会话)，待客户再发言后分配人工 openKfId={}", openKfId);
            } else {
                // 0/1 或 welcome 发出：转人工，保持会话未结束，降低下次落到 state=4 的概率
                enqueueForHumanIfNeeded(openKfId, externalUserId);
            }
        } else {
            // 底线：典型 state=4 且无 welcome_code —— 必须暂存，发言后补发
            savePendingPayGuide(openKfId, externalUserId, title, desc, url);
            log.warn("支付引导未能进线发出(state={})，已暂存；客户发言后补发并分配人工 openKfId={}",
                    state, openKfId);
        }
    }

    /** state=0/1 下用 send_msg 发支付菜单 */
    private boolean trySendMenuBySendMsg(String externalUserId, String openKfId, int state,
                                         Map<String, Object> menuPayload) {
        try {
            if (state == 0) {
                changeServiceState(openKfId, externalUserId, SERVICE_STATE_AI, null, "智能助手接待");
            }
            sendMsg(externalUserId, openKfId, menuPayload);
            log.info("已通过send_msg推送支付蓝字菜单 openKfId={}", openKfId);
            return true;
        } catch (Exception e) {
            log.warn("send_msg支付菜单失败 openKfId={}, reason={}", openKfId, e.getMessage());
            return false;
        }
    }

    /** 文案类引导（错误提示等），仍走文本 */
    private void deliver(String welcomeCode, String openKfId, String externalUserId, Map<String, Object> msgPayload) {
        boolean sent = false;
        if (!isBlank(welcomeCode)) {
            try {
                sendMsgOnEvent(welcomeCode, msgPayload);
                sent = true;
            } catch (Exception e) {
                log.warn("welcome_code发送文案失败 openKfId={}, reason={}", openKfId, e.getMessage());
            }
        }
        if (!sent && !isBlank(openKfId) && !isBlank(externalUserId)) {
            int state = getServiceState(openKfId, externalUserId);
            if (state == 0 || state == SERVICE_STATE_AI) {
                try {
                    if (state == 0) {
                        changeServiceState(openKfId, externalUserId, SERVICE_STATE_AI, null, "智能助手接待");
                    }
                    sendMsg(externalUserId, openKfId, msgPayload);
                    sent = true;
                } catch (Exception e) {
                    log.warn("send_msg发送文案失败 openKfId={}, reason={}", openKfId, e.getMessage());
                }
            }
            if (!sent && state == SERVICE_STATE_WAITING_POOL) {
                sent = sendByMsgCode(assignToOnlineServicer(openKfId, externalUserId), msgPayload, openKfId, "分配");
            }
            if (!sent && state == SERVICE_STATE_HUMAN) {
                String endCode = changeServiceState(openKfId, externalUserId, SERVICE_STATE_ENDED, null, "结束会话以发送文案");
                sent = sendByMsgCode(endCode, msgPayload, openKfId, "结束会话");
            }
        }
        if (sent) {
            int after = getServiceState(openKfId, externalUserId);
            if (after != SERVICE_STATE_ENDED && after != -1) {
                enqueueForHumanIfNeeded(openKfId, externalUserId);
            }
        }
    }

    private boolean sendByMsgCode(String msgCode, Map<String, Object> msgPayload, String openKfId, String via) {
        if (isBlank(msgCode)) {
            log.warn("{}未返回msg_code，无法发送支付引导 openKfId={}", via, openKfId);
            return false;
        }
        try {
            sendMsgOnEvent(msgCode, msgPayload);
            log.info("已通过{}msg_code推送消息 openKfId={}, msgtype={}",
                    via, openKfId, msgPayload.get("msgtype"));
            return true;
        } catch (Exception e) {
            log.warn("{}msg_code发送失败 openKfId={}, reason={}", via, openKfId, e.getMessage());
            return false;
        }
    }

    private int getServiceState(String openKfId, String externalUserId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("open_kfid", openKfId);
            body.put("external_userid", externalUserId);
            JsonNode resp = post(SERVICE_STATE_GET_URL + "?access_token=" + getAccessToken(), body);
            if (resp.path("errcode").asInt(0) != 0) {
                return -1;
            }
            return resp.path("service_state").asInt(-1);
        } catch (Exception e) {
            log.warn("获取会话状态异常 openKfId={}, reason={}", openKfId, e.getMessage());
            return -1;
        }
    }

    private void savePendingPayGuide(String openKfId, String externalUserId,
                                     String title, String desc, String url) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("title", title);
            body.put("desc", desc);
            body.put("url", url);
            redis.opsForValue().set(pendingPayKey(openKfId, externalUserId),
                    objectMapper.writeValueAsString(body), Duration.ofSeconds(PENDING_PAY_MSG_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("暂存支付引导失败 openKfId={}, reason={}", openKfId, e.getMessage());
        }
    }

    private void clearPendingPayMessage(String openKfId, String externalUserId) {
        redis.delete(pendingPayKey(openKfId, externalUserId));
    }

    private void flushPendingPayMessage(String openKfId, String externalUserId) {
        if (isBlank(openKfId) || isBlank(externalUserId)) {
            return;
        }
        String key = pendingPayKey(openKfId, externalUserId);
        String raw = redis.opsForValue().get(key);
        if (isBlank(raw)) {
            return;
        }
        String title = "订单支付";
        String desc = "点击完成支付";
        String url = null;
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.hasNonNull("url")) {
                title = node.path("title").asText(title);
                desc = node.path("desc").asText(desc);
                url = node.path("url").asText(null);
            }
        } catch (Exception ignore) {
            // 兼容旧版：纯 URL 或纯文本
        }
        if (isBlank(url) && raw.startsWith("http")) {
            url = raw.trim();
        }
        if (isBlank(url)) {
            try {
                deliver(null, openKfId, externalUserId, textMsg(raw));
                redis.delete(key);
            } catch (Exception e) {
                log.warn("补发旧版文本支付引导失败 openKfId={}, reason={}", openKfId, e.getMessage());
            }
            return;
        }
        try {
            redis.delete(key);
            // 客户已发言，一般为 state=0，可 send_msg 发菜单并分配人工
            deliverPayGuide(null, openKfId, externalUserId, title, desc, url);
        } catch (Exception e) {
            log.warn("补发暂存支付引导失败 openKfId={}, reason={}", openKfId, e.getMessage());
        }
    }

    private static String pendingPayKey(String openKfId, String externalUserId) {
        return PENDING_PAY_MSG_KEY_PREFIX + openKfId + ":" + externalUserId;
    }

    /**
     * 将会话分配给「接待中」的人工客服。
     * 开启 API 管会话后，仅进待接入池(state=2)不会自动派单，需主动 trans 到 state=3。
     */
    private void enqueueForHumanIfNeeded(String openKfId, String externalUserId) {
        if (isBlank(openKfId) || isBlank(externalUserId)) {
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("open_kfid", openKfId);
            body.put("external_userid", externalUserId);
            JsonNode resp = post(SERVICE_STATE_GET_URL + "?access_token=" + getAccessToken(), body);
            int errcode = resp.path("errcode").asInt(0);
            if (errcode != 0) {
                log.warn("获取会话状态失败 openKfId={}, errcode={}, errmsg={}",
                        openKfId, errcode, resp.path("errmsg").asText(""));
                return;
            }
            int state = resp.path("service_state").asInt(-1);
            log.info("当前会话状态 openKfId={}, externalUserId={}, service_state={}",
                    openKfId, externalUserId, state);
            if (state == SERVICE_STATE_HUMAN) {
                return;
            }
            // 0/1 先落到待接入池，再指定接待人员；已在池中(2)直接指派
            if (state == 0 || state == SERVICE_STATE_AI) {
                changeServiceState(openKfId, externalUserId, SERVICE_STATE_WAITING_POOL, null, "待接入池");
                state = SERVICE_STATE_WAITING_POOL;
            }
            if (state == SERVICE_STATE_WAITING_POOL) {
                assignToOnlineServicer(openKfId, externalUserId);
            }
        } catch (Exception e) {
            log.warn("分配人工客服失败 openKfId={}, reason={}", openKfId, e.getMessage());
        }
    }

    /**
     * 从接待中人员里轮询指派一人（state=3）。
     * @return 变更状态时返回的 msg_code（可用于 send_msg_on_event）；失败返回 null
     */
    private String assignToOnlineServicer(String openKfId, String externalUserId) {
        List<String> online = listOnlineServicers(openKfId);
        if (online.isEmpty()) {
            log.warn("无接待中的客服人员，会话留在待接入池。请在企微将接待人员设为「接待中」 openKfId={}", openKfId);
            return null;
        }
        Long seq = redis.opsForValue().increment(SERVICER_RR_REDIS_KEY_PREFIX + openKfId);
        if (seq == null || seq < 1) {
            seq = 1L;
        }
        String userid = online.get((int) ((seq - 1) % online.size()));
        return changeServiceState(openKfId, externalUserId, SERVICE_STATE_HUMAN, userid, "人工接待:" + userid);
    }

    /** 拉取客服账号下 status=0（接待中）的接待人员 userid 列表 */
    private List<String> listOnlineServicers(String openKfId) {
        List<String> result = new ArrayList<>();
        try {
            String url = SERVICER_LIST_URL + "?access_token=" + getAccessToken()
                    + "&open_kfid=" + URLEncoder.encode(openKfId, StandardCharsets.UTF_8);
            JsonNode resp = get(url);
            if (resp.path("errcode").asInt(0) != 0) {
                log.warn("获取接待人员列表失败 openKfId={}, errcode={}, errmsg={}",
                        openKfId, resp.path("errcode").asInt(), resp.path("errmsg").asText(""));
                return result;
            }
            JsonNode list = resp.path("servicer_list");
            if (!list.isArray()) {
                return result;
            }
            for (JsonNode item : list) {
                if (item.path("status").asInt(-1) == SERVICER_STATUS_ONLINE) {
                    String userid = item.path("userid").asText(null);
                    if (!isBlank(userid)) {
                        result.add(userid);
                    }
                }
            }
            log.info("接待人员列表 openKfId={}, onlineCount={}, total={}",
                    openKfId, result.size(), list.size());
        } catch (Exception e) {
            log.warn("获取接待人员列表异常 openKfId={}, reason={}", openKfId, e.getMessage());
        }
        return result;
    }

    /**
     * 变更会话状态；失败仅打日志。
     * @return 成功时的 msg_code（转待接入/人工/结束时可能有），失败返回 null
     */
    private String changeServiceState(String openKfId, String externalUserId, int serviceState,
                                      String servicerUserid, String label) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("open_kfid", openKfId);
            body.put("external_userid", externalUserId);
            body.put("service_state", serviceState);
            if (!isBlank(servicerUserid)) {
                body.put("servicer_userid", servicerUserid);
            }
            JsonNode resp = post(SERVICE_STATE_TRANS_URL + "?access_token=" + getAccessToken(), body);
            int errcode = resp.path("errcode").asInt(0);
            if (errcode != 0) {
                log.warn("切换会话状态未成功 target={}, openKfId={}, errcode={}, errmsg={}",
                        label, openKfId, errcode, resp.path("errmsg").asText(""));
                return null;
            }
            String msgCode = resp.path("msg_code").asText(null);
            log.info("已切换会话状态 target={}, openKfId={}, hasMsgCode={}",
                    label, openKfId, !isBlank(msgCode));
            return isBlank(msgCode) ? null : msgCode;
        } catch (Exception e) {
            log.warn("切换会话状态异常 target={}, openKfId={}, reason={}", label, openKfId, e.getMessage());
            return null;
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

    /** 支付蓝字菜单：可点「打开支付页面」；desc 含商品/金额/下单与截止时间，便于区分多笔订单 */
    private Map<String, Object> payMenuMsg(String title, String desc, String url) {
        Map<String, Object> map = new HashMap<>();
        map.put("msgtype", "msgmenu");
        Map<String, Object> menu = new HashMap<>();
        menu.put("head_content", title + "\n" + desc + "\n请点击下方链接完成支付");
        Map<String, Object> viewItem = new HashMap<>();
        viewItem.put("type", "view");
        Map<String, Object> view = new HashMap<>();
        view.put("url", url);
        view.put("content", "打开支付页面");
        viewItem.put("view", view);
        menu.put("list", List.of(viewItem));
        menu.put("tail_content", "链接15分钟内有效，超时请回小程序重新发起");
        map.put("msgmenu", menu);
        return map;
    }

    /** 拼支付菜单正文，方便用户在历史气泡中区分订单 */
    private String buildPayMenuDesc(Order order) {
        StringBuilder sb = new StringBuilder();
        String product = !isBlank(order.getProductName()) ? order.getProductName() : "待支付订单";
        sb.append("商品：").append(product);
        if (order.getAmount() != null) {
            sb.append("\n金额：¥").append(formatAmount(order.getAmount()));
        }
        if (order.getCreatedAt() != null) {
            sb.append("\n下单：").append(formatPayMsgTime(order.getCreatedAt()));
        }
        if (order.getPayDeadline() != null) {
            sb.append("\n支付截止：").append(formatPayMsgTime(order.getPayDeadline()));
        }
        String orderNo = order.getOrderNo();
        if (!isBlank(orderNo)) {
            String tail = orderNo.length() <= 6 ? orderNo : orderNo.substring(orderNo.length() - 6);
            sb.append("\n单号尾号：").append(tail);
        }
        return sb.toString();
    }

    private static String formatPayMsgTime(LocalDateTime time) {
        return time.format(PAY_MSG_TIME_FMT);
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
