# 客服会话 H5 支付 — 实现完成与上线检查清单

> 日期：2026-07-15  
> 对应 Spec：`docs/superpowers/specs/2026-07-07-kf-h5-payment-design.md`  
> 对应 Plan：`docs/superpowers/plans/2026-07-15-kf-h5-payment-implementation.md`

## 代码交付状态

| Task | 内容 | Git |
|------|------|-----|
| 1–3 | pay_channel / PayToken / kf token API / createPrepayOrder 参数化 | 已含在 `0473ec8 init` |
| 4–5 | H5 API + Security 白名单 + 企微客服回调自动发链接 | `01191ad` |
| 6–7 | 小程序「联系客服支付」+ H5 `pages/order/h5pay` | `15d959d` |
| 8 | 本清单 | 本提交 |

## 上线前必填配置

1. 执行 SQL：`docs/migrations/2026-07-15-payment-pay-channel.sql`
2. `application-*.yml`：
   - `wx.mp.appid` / `wx.mp.secret`（服务号）
   - `wx.kf.corp-id` / `secret` / `callback-token` / `callback-aes-key`
   - `pay.kf.token-secret`（随机强密钥）
   - `pay.kf.h5-pay-base-url`（真实 H5 地址，如 `https://域名/#/pages/order/h5pay`）
   - `wx.pay.enabled=true` 且商户号已绑定**服务号** appid
3. `SecurityConfig` 已放行：`/pay/wxkf/callback`、`/pay/h5/**`（无需再改）
4. 服务号后台：网页授权域名、JS 安全域名
5. 企微「微信客服 → 开发配置」：回调 URL 指向 `https://API域名/pay/wxkf/callback`，点验证
6. 小程序侧：`cs.corp_id`、`cs.service_url`、`cs.contact_mode=auto|wework` 已可用

## 部署顺序（强约束）

1. 先上后端（含迁移 + 配置）
2. 再发小程序（微信支付改为联系客服）并部署 H5 构建产物
3. 禁止先发小程序后上后端

## 验收清单（上线联调）

- [ ] 小程序余额支付仍成功
- [ ] 小程序点「联系客服支付」能打开企微会话，且带 scene_param
- [ ] 进会话 20s 内收到「订单支付」链接
- [ ] 客服唤起失败时不弹二维码（支付场景）
- [ ] 非微信打开 H5 → 拦截「请在微信中打开」
- [ ] 微信内 H5：授权 → 看单（券只读）→ 支付成功 → 订单 PAID / 核销 / 指派
- [ ] 付完再开链接 → 提示已支付
- [ ] token 篡改/过期 → 拒绝

## 已知遗留

- 押金支付仍走小程序直付（按 Spec 非目标）
- H5 成功页跳回小程序依赖 URL Scheme/Link，未配置时仅文案引导
- 真机联调与 `npm run build:h5` 需在本地环境完成
