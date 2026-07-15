# 客服会话 H5 支付链路 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 小程序微信支付改为「联系客服 → 自动推送 H5 支付链接 → 服务号 JSAPI」，余额支付与押金保持现状。

**Architecture:** 用紧凑 `payToken` 经微信客服 `scene_param` 透传订单；客服回调机器人 `send_msg_on_event` 推独立 H5 页；H5 轻量 `snsapi_base` 换服务号 openid 后支付；回调复用 `handleWxPayV3Notify`。不作废 token，靠订单 `PENDING_PAYMENT` + TTL。

**Tech Stack:** Spring Boot / MyBatis-Plus / Redis / 微信客服 API / 微信支付 V3 JSAPI / uni-app（小程序 + H5）

**Spec:** `docs/superpowers/specs/2026-07-07-kf-h5-payment-design.md`（含 Grill 决议）

## Global Constraints

- Grill 决议 #1–#12 全部生效（见 Spec §0），尤其：余额留小程序、H5 只读券、禁止支付场景二维码降级、非微信 UA 拦截、无 `consume`。
- 部署顺序：先后端，再发小程序 + H5。
- H5/小程序支付相关 UI 遵循 **ui-ux-pro-max** + **现有 Delta 设计语言**（禁止另起一套紫色/奶油纸风）：
  - 背景 `#f1f5f9`，主文 `#1e293b`，次文 `#64748b` / `#94a3b8`，主色/CTA `#ff4544`（与 `pay.vue` 一致）。
  - 一屏一事：首屏只放金额、只读券、一个主 CTA；成功页同理。
  - 触控目标 ≥44×44pt；按下态用透明度/背景色，禁止布局位移；禁用态视觉明确。
  - 不用 emoji 当图标；复用现有 `/static/icons/*.svg`。
  - 文案低调：客服消息与 H5 **避免「代练」字样**（用「订单支付」「确认支付」）。
  - 错误提示清晰、可重试；加载态不可点。

---

## File Structure

| 路径 | 职责 |
|---|---|
| `delta_game.sql` 或迁移 SQL | `payment.pay_channel` |
| `delta-pay/.../entity/Payment.java` | 加 `payChannel` |
| `delta-pay/.../service/PayTokenService.java` + `impl` + `PayTokenPayload` | 签发/校验 |
| `delta-pay/.../config/WxPayConfiguration.java` | `mpAppId` |
| `delta-pay/.../controller/PayController.java` | `createPrepayOrder` 参数化；写 `pay_channel` |
| `delta-pay/.../controller/H5PayController.java` | oauth/order/prepay/jsconfig |
| `delta-pay/.../wxkf/*` | 加解密、回调、发消息 |
| `delta-common/.../SecurityConfig.java` | 放行 `/pay/wxkf/**`、`/pay/h5/**` |
| `application-*.yml` | `wx.mp.*`、`wx.kf.*`、`pay.kf.*` |
| `delta-mp/api/pay.js` | 增 `getPayKfToken` 等 |
| `delta-mp/composables/useWeworkCs.js` | `payToken` + `scene=pay` 禁二维码 |
| `delta-mp/pages/order/pay.vue` | 微信支付 → 联系客服 |
| `delta-mp/pages/order/h5pay.vue` | 独立 H5 支付页 |
| `delta-mp/pages.json` | 注册 `h5pay` |
| `delta-pay/src/test/.../PayTokenServiceTest.java` | 单测 |

---

### Task 1: DB + Payment 实体 + 配置位

**Files:**
- Modify: `delta_game.sql`（payment 表定义处追加列注释，或新增 `docs/migrations/2026-07-15-payment-pay-channel.sql`）
- Modify: `delta-game/delta-pay/src/main/java/com/delta/pay/entity/Payment.java`
- Modify: `delta-game/delta-admin/src/main/resources/application-dev.yml`（test/prod 同步加空占位）
- Modify: `delta-game/delta-pay/src/main/java/com/delta/pay/config/WxPayConfiguration.java`

**Interfaces:**
- Produces: `Payment.payChannel` (`String`, 取值 `MINIAPP` / `MP_H5`)；`WxPayConfiguration.getMpAppId()`；yml 键见下

- [ ] **Step 1: 写迁移 SQL**

```sql
-- docs/migrations/2026-07-15-payment-pay-channel.sql
ALTER TABLE payment
  ADD COLUMN pay_channel VARCHAR(16) DEFAULT NULL COMMENT '支付渠道: MINIAPP/MP_H5' AFTER pay_method;
```

- [ ] **Step 2: Payment 加字段**

在 `Payment.java` 的 `payMethod` 后增加：

```java
/** 支付渠道: MINIAPP-小程序 JSAPI / MP_H5-服务号 H5 JSAPI */
private String payChannel;
```

- [ ] **Step 3: yml 占位（dev 示例，secret 先空字符串，上线再填）**

在现有 `wx:` 下追加：

```yaml
  mp:
    appid: ""          # 服务号 appid
    secret: ""         # 服务号 secret（网页授权）
  kf:
    corp-id: ""
    secret: ""
    callback-token: ""
    callback-aes-key: ""
pay:
  kf:
    token-secret: "CHANGE_ME_TO_RANDOM_32+"
    token-ttl-seconds: 900
    h5-pay-base-url: "https://YOUR_H5_HOST/#/pages/order/h5pay"
```

- [ ] **Step 4: WxPayConfiguration 增加 mpAppId**

```java
@Getter
@Value("${wx.mp.appid:}")
private String mpAppId;
```

保留现有 `appId`（来自 `wx.miniapp.appid`）给小程序支付用。

- [ ] **Step 5: Commit**

```bash
git add docs/migrations/2026-07-15-payment-pay-channel.sql \
  delta-game/delta-pay/src/main/java/com/delta/pay/entity/Payment.java \
  delta-game/delta-pay/src/main/java/com/delta/pay/config/WxPayConfiguration.java \
  delta-game/delta-admin/src/main/resources/application-dev.yml \
  delta-game/delta-admin/src/main/resources/application-test.yml \
  delta-game/delta-admin/src/main/resources/application-prod.yml
git commit -m "$(cat <<'EOF'
feat(pay): add pay_channel column and H5/kf config placeholders

EOF
)"
```

---

### Task 2: PayTokenService（TDD）

**Files:**
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/service/PayTokenService.java`
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/service/impl/PayTokenServiceImpl.java`
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/domain/PayTokenPayload.java`
- Create: `delta-game/delta-pay/src/test/java/com/delta/pay/service/impl/PayTokenServiceImplTest.java`
- Modify: 若 `delta-pay` 无 test 依赖，在其 `pom.xml` 加 junit/mockito（对齐 sibling module）

**Interfaces:**
- Produces:
  - `record PayTokenPayload(Long orderId, Long userId, long exp, String jti)`
  - `String issue(Long orderId, Long userId)`
  - `PayTokenPayload verify(String token)` — 校验签名/过期/可选 Redis jti；**不查订单**（订单状态由调用方查，便于单测）
- Consumes: `StringRedisTemplate`；`@Value("${pay.kf.token-secret}")`；`@Value("${pay.kf.token-ttl-seconds:900}")`

- [ ] **Step 1: 写失败单测（先不实现）**

```java
@ExtendWith(MockitoExtension.class)
class PayTokenServiceImplTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    PayTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new PayTokenServiceImpl(redis, "test-secret-key-32bytes!!!!!!", 900);
    }

    @Test
    void issue_then_verify_roundTrip() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        String token = service.issue(1001L, 2002L);
        assertTrue(token.split("\\.").length == 5);
        assertTrue(token.length() < 120);
        PayTokenPayload p = service.verify(token);
        assertEquals(1001L, p.orderId());
        assertEquals(2002L, p.userId());
    }

    @Test
    void verify_tamperedSig_throws() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        String token = service.issue(1L, 2L);
        String bad = token.substring(0, token.length() - 2) + "aa";
        assertThrows(BusinessException.class, () -> service.verify(bad));
    }

    @Test
    void verify_expired_throws() {
        // 构造已过期 token：直接测内部或临时 ttl=-1 的 helper；实现时提供 package-private buildTokenForTest
        String token = service.buildTokenForTest(1L, 2L, Instant.now().getEpochSecond() - 10, "abcdefgh");
        assertThrows(BusinessException.class, () -> service.verify(token));
    }
}
```

- [ ] **Step 2: 跑测确认失败**

```bash
cd delta-game && mvn -pl delta-pay -am test -Dtest=PayTokenServiceImplTest
```

Expected: 编译失败或 FAIL（类不存在）。

- [ ] **Step 3: 实现紧凑格式**

格式：`orderId.userId.exp.jti.sig`  
`sig = base64url(HMAC-SHA256(orderId.userId.exp.jti, secret)[0..16))`  
`jti` = 10 位 `[a-z0-9]` 随机  

签发：

```java
String jti = randomJti(10);
long exp = Instant.now().getEpochSecond() + ttlSeconds;
String body = orderId + "." + userId + "." + exp + "." + jti;
String sig = sign(body);
String token = body + "." + sig;
redis.opsForValue().set("pay:kf:token:" + jti, String.valueOf(orderId), Duration.ofSeconds(ttlSeconds));
// 限流：INCR pay:kf:rl:{orderId} EX 60，>10 则抛业务异常
return token;
```

校验：拆 5 段 → 验签 → `exp > now` →（若 Redis 有该 jti key，或 key 缺失策略：**签名+exp 通过即可**，Redis 仅防刷辅助；推荐：key 缺失也允许，避免 Redis 闪断误杀）——按 Spec「主防线是订单状态」。实现取：**签名+未过期即通过**；Redis set 用于限流与可选观测。

- [ ] **Step 4: 跑测 PASS**

```bash
cd delta-game && mvn -pl delta-pay -am test -Dtest=PayTokenServiceImplTest
```

- [ ] **Step 5: Commit**

```bash
git add delta-game/delta-pay/src/main/java/com/delta/pay/service/PayTokenService.java \
  delta-game/delta-pay/src/main/java/com/delta/pay/service/impl/PayTokenServiceImpl.java \
  delta-game/delta-pay/src/main/java/com/delta/pay/domain/PayTokenPayload.java \
  delta-game/delta-pay/src/test/java/com/delta/pay/service/impl/PayTokenServiceImplTest.java
git commit -m "$(cat <<'EOF'
feat(pay): add compact payToken issue/verify service

EOF
)"
```

---

### Task 3: 参数化 createPrepayOrder + 签发 token API

**Files:**
- Modify: `delta-game/delta-pay/src/main/java/com/delta/pay/controller/PayController.java`
- Modify: `delta-game/delta-pay/src/main/java/com/delta/pay/service/PaymentService.java`（若 createWxPayment 需写 channel，可在 Controller 设值后 update）

**Interfaces:**
- Produces: `GET /pay/kf/token/{orderId}` → `{ token, expireAt }`
- Produces: `createPrepayOrder(..., String appid, String openid)` — 私有方法签名变更
- Consumes: `PayTokenService.issue`；`SecurityUtils.getUserId()`；`OrderService`

- [ ] **Step 1: 改 `createPrepayOrder` 使用入参 appid**

将：

```java
request.setAppid(wxPayConfiguration.getAppId());
```

改为入参 `appid`。`wxPay` / `playerDeposit` 调用处传 `wxPayConfiguration.getAppId()`（小程序）。

小程序成功下单后：

```java
payment.setPayChannel("MINIAPP");
paymentService.updateById(payment);
```

- [ ] **Step 2: 新增 token 签发接口（同 PayController 或独立）**

```java
@GetMapping("/kf/token/{orderId}")
public R<Map<String, Object>> issueKfPayToken(@PathVariable Long orderId) {
    Long userId = SecurityUtils.getUserId();
    Order order = orderService.getById(orderId);
    if (order == null) return R.fail("订单不存在");
    if (!order.getUserId().equals(userId)) return R.fail("无权操作");
    if (!OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus())) {
        return R.fail("订单状态不允许支付");
    }
    String token = payTokenService.issue(orderId, userId);
    Map<String, Object> data = new HashMap<>();
    data.put("token", token);
    data.put("expireSeconds", /* ttl from config */);
    return R.ok(data);
}
```

- [ ] **Step 3: 本地冒烟**

- 登录态调 `GET /pay/kf/token/{待付订单}` → 200 + token 5 段  
- 非本人订单 → 失败  
- 已支付订单 → 失败  

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(pay): parameterize JSAPI appid and add kf token API

EOF
)"
```

---

### Task 4: H5PayController + Security 白名单

**Files:**
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/controller/H5PayController.java`
- Modify: `delta-game/delta-common/src/main/java/com/delta/common/config/SecurityConfig.java`
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/service/MpOAuthService.java`（code→openid）
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/service/MpJsapiSignService.java`（jsconfig）

**Interfaces:**
- Produces:
  - `POST /pay/h5/oauth` body `{ code }` → `{ openid }`
  - `GET /pay/h5/order?token=` → `{ orderId, orderNo, productName, amount, couponName, couponDiscountAmount, payDeadline, status }`
  - `POST /pay/h5/prepay` body `{ token, openid }` → `{ timeStamp, nonceStr, package, signType, paySign }`
  - `GET /pay/h5/jsconfig?url=` → `{ appId, timestamp, nonceStr, signature }`
- Consumes: `PayTokenService.verify`；`OrderService`；`PaymentService.createWxPayment`；`WxPayConfiguration.getMpAppId()`；HttpClient 调微信 `sns/oauth2/access_token`

- [ ] **Step 1: SecurityConfig 放行**

在 `.requestMatchers("/pay/wx/notify").permitAll()` 旁增加：

```java
.requestMatchers("/pay/wxkf/callback").permitAll()
.requestMatchers("/pay/h5/**").permitAll()
```

- [ ] **Step 2: oauth**

```java
// GET https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code
```

失败码明确返回业务错误；openid 为空则 fail。

- [ ] **Step 3: order**

```java
PayTokenPayload p = payTokenService.verify(token);
Order order = orderService.getById(p.orderId());
if (order == null) throw new BusinessException("订单不存在");
if (!OrderStatusEnum.PENDING_PAYMENT.name().equals(order.getStatus())) {
    throw new BusinessException("订单状态已变更"); // 或细分 PAID / CANCELLED 文案
}
// 返回只读字段，禁止返回可改券列表
```

- [ ] **Step 4: prepay**

```java
PayTokenPayload p = payTokenService.verify(token);
// 再次确认 PENDING_PAYMENT
Payment payment = paymentService.createWxPayment(p.orderId(), p.userId());
payment.setPayChannel("MP_H5");
paymentService.updateById(payment);
// createPrepayOrder(jsapi, cfg, payment, cfg.getMpAppId(), openid, description)
```

**禁止**接收 `userCouponId`。

- [ ] **Step 5: jsconfig**

对传入 page `url`（需与微信 JSSDK 当前 url 一致，hash 模式注意）用服务号 jsapi_ticket 签名（ticket Redis 缓存）。

- [ ] **Step 6: 手工验证**（可用 Postman 模拟非法 token）后 Commit

```bash
git commit -m "$(cat <<'EOF'
feat(pay): add H5 oauth/order/prepay/jsconfig endpoints

EOF
)"
```

---

### Task 5: 微信客服回调与自动发链接

**Files:**
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/wxkf/WxKfCrypt.java`
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/wxkf/WxKfService.java`
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/wxkf/WxKfController.java`
- Create: `delta-game/delta-pay/src/main/java/com/delta/pay/wxkf/WxKfProperties.java`（`@ConfigurationProperties(prefix="wx.kf")`）

**Interfaces:**
- Produces: `GET/POST /pay/wxkf/callback`
- Consumes: `PayTokenService.verify`；`OrderService`；`pay.kf.h5-pay-base-url`；企业微信 `gettoken` / `kf/sync_msg` / `kf/send_msg_on_event`

- [ ] **Step 1: WxKfCrypt**

复用企业微信官方 `WXBizMsgCrypt` 算法（可拷贝已验证实现到本包，Token/AESKey/CorpId 来自配置）。实现：

- `verifyURL(msgSignature, timestamp, nonce, echostr) → String`
- `decryptMsg(...) → String xml/json`

注意：微信客服回调可能是 JSON；以企微客服文档为准解析 `Token` / `OpenKfId`。

- [ ] **Step 2: access_token + sync_msg**

```java
// Redis key: pay:kf:access_token
// GET https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=&corpsecret=
// POST https://qyapi.weixin.qq.com/cgi-bin/kf/sync_msg?access_token=
// body: { cursor, token, limit, open_kfid }
// 持久化 cursor: pay:kf:cursor:{open_kfid}
```

- [ ] **Step 3: 处理 enter_session**

伪代码：

```java
String sceneParam = event.scene_param; // = payToken
String welcomeCode = event.welcome_code;
if (sceneParam == null || sceneParam.isBlank()) {
    sendText(openKfId, externalUserId, "请回到小程序重新发起支付");
    return;
}
try {
    PayTokenPayload p = payTokenService.verify(sceneParam);
    Order order = orderService.getById(p.orderId());
    if (order == null) { /* 引导 */ return; }
    if ("PAID".equals(order.getStatus())) {
        sendMsgOnEvent(welcomeCode, text("该订单已支付，无需重复支付"));
        return;
    }
    if (!"PENDING_PAYMENT".equals(order.getStatus())) {
        sendMsgOnEvent(welcomeCode, text("订单状态已变更，请回到小程序查看"));
        return;
    }
    String link = h5PayBaseUrl + (h5PayBaseUrl.contains("?") ? "&" : "?") + "token=" + URLEncoder.encode(sceneParam, UTF_8);
    // 图文/链接消息：title 用「订单支付」，description 用商品名/金额，禁止敏感词
    sendMsgOnEvent(welcomeCode, linkMsg("订单支付", order.getProductName(), link));
} catch (BusinessException e) {
    sendMsgOnEvent(welcomeCode, text("支付链接已失效，请回到小程序重新发起"));
}
```

`welcome_code` 为空时降级 `kf/send_msg`（需 48h 窗口）。

- [ ] **Step 4: Controller**

GET → verifyURL 明文 echo  
POST → decrypt → sync → handle → 返回 `"success"`

- [ ] **Step 5: 在企微后台配置回调并点「验证」** Expected: 通过  

- [ ] **Step 6: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(pay): auto-send H5 pay link on WeCom KF enter_session

EOF
)"
```

---

### Task 6: 小程序端 — 支付页改联系客服

**Files:**
- Modify: `delta-mp/api/pay.js`
- Modify: `delta-mp/composables/useWeworkCs.js`
- Modify: `delta-mp/pages/order/pay.vue`

**Interfaces:**
- Produces: `getPayKfToken(orderId)`；`openWeworkCs({ scene:'pay', payToken, order })`
- Consumes: `GET /pay/kf/token/{orderId}`；`cs.service_url` + `scene_param`

- [ ] **Step 1: api**

```js
export const getPayKfToken = (orderId) => get(`/pay/kf/token/${orderId}`)
```

- [ ] **Step 2: useWeworkCs 支持 payToken + 禁止 pay 场景二维码**

```js
async function openWeworkCs({ scene = 'general', order, product, payToken } = {}) {
  // ...
  let serviceUrl = csServiceUrl
  if (payToken) {
    const sep = serviceUrl.includes('?') ? '&' : '?'
    serviceUrl = `${serviceUrl}${sep}scene_param=${encodeURIComponent(payToken)}`
  }
  // openCustomerServiceChat extInfo.url = serviceUrl

  // fail / 无 API 时：
  if (scene === 'pay') {
    uni.showToast({ title: '客服暂时不可用，请稍后重试', icon: 'none' })
    return  // 禁止 showQrModal
  }
  // 其他 scene 保持原二维码逻辑
}
```

`SCENE_TITLE.pay = '订单支付'`（或按钮文案「联系客服支付」）。

- [ ] **Step 3: pay.vue**

- 保留余额分支不动。  
- 微信支付：`canUseWechatPay` 仍可显示入口，但 `handlePay` 微信分支改为：

```js
const res = await getPayKfToken(orderId.value)
await openWeworkCs({ scene: 'pay', payToken: res.data.token, order: { id: orderId.value } })
```

- UI：微信方式文案改为「联系客服支付」；主 CTA 仍用现有红色胶囊按钮；图标继续用 `/static/icons/钞票.svg`。  
- 加载中禁用按钮（opacity 0.6 + 不可点）。

- [ ] **Step 4: 微信开发者工具冒烟** — 点联系客服能打开会话（回调通后应收链接）

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(mp): route WeChat pay through customer service with scene_param

EOF
)"
```

---

### Task 7: H5 独立页 `pages/order/h5pay`（ui-ux-pro-max）

**Files:**
- Create: `delta-mp/pages/order/h5pay.vue`
- Modify: `delta-mp/pages.json`（注册页面）
- Modify: `delta-mp/api/pay.js`（h5Oauth / h5Order / h5Prepay / h5Jsconfig）
- Create: `delta-mp/utils/weixinUa.js`（可选）`isWeixinBrowser()`

**UI 规格（必须遵守）：**

| 状态 | 内容 |
|---|---|
| 非微信 | 全屏居中：标题「请在微信中打开」、一句说明、无其它营销块 |
| 加载 | 金额区骨架或居中 loading，主按钮 disabled |
| 待支付 | 金额（`PriceText`）+ 倒计时 + 只读券行 + 单一 CTA「微信支付」 |
| 失败/失效 | 明确文案 + 「返回」提示，无第二支付入口 |
| 成功 | 成功标题 + 金额/订单号 + 主按钮「打开小程序查看订单」+ 次文案兜底 |

视觉 token：背景 `#f1f5f9`，卡片白底圆角 `12rpx`，CTA `linear-gradient(135deg, #ff4544, #e63939)`，字号层级对齐 `pay.vue`。  
一屏一事：首屏**不要**放统计、地址、推荐商品。触控高度 ≥ `88rpx`。

- [ ] **Step 1: pages.json 注册**

```json
{
  "path": "pages/order/h5pay",
  "style": {
    "navigationBarTitleText": "订单支付",
    "navigationBarTextStyle": "black",
    "backgroundColor": "#f1f5f9"
  }
}
```

- [ ] **Step 2: 入口逻辑骨架**

```js
onLoad(async (opts) => {
  if (!isWeixinBrowser()) { blocked.value = true; return }
  token.value = opts.token || ''
  // 处理 OAuth 回调 ?code=&state=token
  if (opts.code) {
    const r = await h5Oauth({ code: opts.code })
    sessionStorage.setItem('mp_openid', r.data.openid)
    // 清 URL 上 code，保留 token
  }
  if (!sessionStorage.getItem('mp_openid')) {
    redirectToOAuth(token.value) // snsapi_base, state=token
    return
  }
  await loadOrder()
  await initWxConfig() // jsconfig + wx.config
})
```

- [ ] **Step 3: 支付**

```js
const prepay = await h5Prepay({ token: token.value, openid })
wx.chooseWXPay({ ...prepay, success: () => { paid.value = true } })
```

成功后：

```js
// 优先：配置的小程序 URL Link / URL Scheme 跳转 pages/order/detail?id=
// 失败：展示文案「请返回微信小程序查看订单」
```

- [ ] **Step 4: H5 编译验证**

```bash
cd delta-mp && npm run build:h5
# 确认产物含 h5pay，本地用微信开发者工具或真机微信打开带 token 的 URL
```

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(h5): add standalone WeChat JSAPI pay page with UA guard

EOF
)"
```

---

### Task 8: 端到端验收清单

**Files:** 无新代码（可更新 Spec 状态为「已实现待上线」）

- [ ] **Step 1: 按 Spec §13 逐条验收**

| # | 用例 | Expected |
|---|---|---|
| 1 | token 篡改/过期 | H5/客服均拒绝 |
| 2 | 小程序余额支付 | 与改造前一致成功 |
| 3 | 小程序微信 → 客服 | 会话内收到支付链接 |
| 4 | 客服唤起失败 | **无二维码**，toast 重试 |
| 5 | 非微信打开 H5 | 拦截页 |
| 6 | H5 支付成功 | 订单 PAID、券核销、指派逻辑正常 |
| 7 | 付完再打开链接 | 提示已支付，prepay 失败 |
| 8 | 旧 `/pay/wx` | 仍受 `wx.pay.enabled` 控制可用 |
| 9 | UI | 对比 pay.vue 色板一致；主 CTA ≥88rpx；无 emoji 图标；成功页一屏一事 |

- [ ] **Step 2: 上线检查** Spec §11.3（白名单、授权域名、JS 安全域名、客服回调、商户 APPID 绑定、`h5-pay-base-url`）

- [ ] **Step 3: 最终 Commit（若有文档状态更新）**

```bash
git commit -m "$(cat <<'EOF'
docs: mark kf-h5 payment spec ready for release checklist

EOF
)"
```

---

## Spec Coverage Self-Review

| Spec 项 | Task |
|---|---|
| payToken 紧凑格式 / 无 consume | T2 |
| 小程序余额保留 + 微信改客服 | T6 |
| scene=pay 禁二维码 | T6 |
| 客服 enter_session 自动发链 | T5 |
| H5 独立页 + UA 拦截 + oauth + 只读券 + 成功页 | T7 |
| createPrepayOrder 参数化 + pay_channel | T1, T3, T4 |
| Security 白名单 | T4 |
| 部署/兼容 / 押金不动 | Global + T8 |
| ui-ux-pro-max | Global + T6/T7 |

无占位符 TBD。`verify` 在 T2 不查订单、T4/T5 查订单，签名一致。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-15-kf-h5-payment-implementation.md`.

**两种执行方式：**

1. **Subagent-Driven（推荐）** — 每任务新开子代理，任务间审查  
2. **Inline Execution** — 本会话按 `executing-plans` 连续推进并设检查点  

选哪种？
