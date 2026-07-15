# 企微客服接入设计

> 日期：2026-06-05  
> 状态：待评审  
> 范围：用户端小程序客服/投诉入口 → 企业微信

## 背景

- 用户端「客服」目前走 `createCsSession` + WebSocket 聊天页，与真实客服体系（企业微信）脱节。
- 用户端「投诉」走小程序内表单 `/pages/complaint/create`，客服在 `pages-cs` 处理。
- 小程序尚未上线，无真实用户数据；**投诉后端 API 与数据库表保持不变**，用户端流程直接替换为企微。
- 企业微信已开通，**小程序 ↔ 企微客服尚未绑定**（阶段一用二维码，绑定后自动升级 API）。

## 目标

1. 所有用户端「客服」入口统一跳转企微（二维码兜底 → 绑定后 API 直跳）。
2. 所有用户端「投诉」入口统一跳转企微，自动复制订单上下文。
3. **保留** 用户 ↔ 接单员 订单聊天（WebSocket）。
4. **保留** `pages-cs` 客服工作台（内部运营）。
5. **移除** 用户端投诉表单/列表入口（页面文件可保留但不再注册路由）。

## 非目标

- 不修改投诉相关 REST API、数据库表、实体类。
- 不改造 `pages-cs` 投诉管理模块。
- 不改造打手端/客服端聊天逻辑。

---

## 架构

```
用户点击「客服」或「投诉」
        │
        ▼
 useWeworkCs({ scene, order?, product? })
        │
        ├─ contact_mode = wework 且 corpId + serviceUrl 齐全
        │       └─ wx.openCustomerServiceChat() ──失败──┐
        │                                               │
        └─ contact_mode = qrcode / auto 降级 ────────────┤
                                                        ▼
                                              CsContactModal（二维码弹窗）
                                              + 复制上下文文案
```

### 场景（scene）

| scene | 触发入口 | 弹窗标题 | 复制文案 |
|-------|---------|---------|---------|
| `general` | 首页悬浮客服、我的-客服 | 联系客服 | 无（或仅提示语） |
| `product` | 商品详情-客服 | 咨询商品 | `【商品咨询】{name} ¥{price}（ID:{id}）` |
| `complaint` | 订单详情-投诉 | 投诉反馈 | `【投诉】订单号:{orderNo} 商品:{productName} 状态:{status}` |

---

## 配置项

通过现有 `GET /system/config/site` 下发，在 `SysConfigController.ensureBuiltinConfigs()` 中 `ensureConfig` 新增（**无新接口、无新表**）：

| configKey | 类型 | 默认值 | 说明 |
|-----------|------|--------|------|
| `cs.contact_mode` | text | `qrcode` | `qrcode` / `wework` / `auto` |
| `cs.corp_id` | text | 空 | 企业微信 corpId，绑定后填写 |
| `cs.service_url` | text | 空 | 企微客服链接，绑定后填写 |
| `cs.qrcode_url` | image | 空 | 「联系我」二维码图片 URL |
| `cs.contact_tips` | text | `长按识别二维码，添加客服微信` | 弹窗底部提示 |

管理后台在「系统配置 → 站点配置」分组自动展示（已有 `SysConfig.vue` 通用表单，无需改 UI 代码）。

### 阶段切换

1. **现在（未绑定）**：`cs.contact_mode = qrcode`，上传 `cs.qrcode_url`。
2. **绑定完成后**：填写 `cs.corp_id`、`cs.service_url`，改 `cs.contact_mode = auto`。

---

## 新增文件

### `delta-mp/composables/useWeworkCs.js`

- 从 `siteStore` 读取客服配置。
- `openWeworkCs(options)` 统一入口。
- `#ifdef MP-WEIXIN` 内调用 `wx.openCustomerServiceChat`；非微信端/H5 直接弹二维码。
- 失败或 `qrcode` 模式 → 设置 modal 状态（`visible`、`title`、`copyText`、`qrcodeUrl`、`tips`）。
- `buildCopyText(scene, order, product)` 生成复制文案。

### `delta-mp/components/CsContactModal.vue`

- 底部滑出弹窗（风格对齐 `CouponPicker`）。
- 展示二维码 `image`、提示语、可选「复制订单信息」按钮。
- `uni.setClipboardData` 复制后 toast 提示。
- 通过 `v-model:visible` 或 composable 暴露的状态驱动。

---

## 修改文件

### `delta-mp/store/site.js`

新增 ref：`csContactMode`、`csCorpId`、`csServiceUrl`、`csQrcodeUrl`、`csContactTips`，在 `fetchSiteConfig` 中赋值。

### 用户端页面（替换入口）

| 文件 | 改动 |
|------|------|
| `pages/index/index.vue` | `goCustomerService` → `openWeworkCs({ scene: 'general' })`，移除 `createCsSession` |
| `pages/mine/index.vue` | 同上；**删除**「我的投诉工单」菜单项 |
| `pages/product/detail.vue` | `goChat` → `openWeworkCs({ scene: 'product', product })`，移除 `createCsSession`/`sendChatMessage` |
| `pages/order/detail.vue` | `goComplaint` → `openWeworkCs({ scene: 'complaint', order })`；**`goChat` 保持不变**（接单员聊天） |

各页面引入 `CsContactModal`（或在 `App.vue` 全局挂载一份）。

### `delta-mp/pages.json`

从 `pages` 数组移除用户端投诉页（避免误访问）：

- `pages/complaint/create`
- `pages/complaint/list`
- `pages/complaint/detail`

> 页面 `.vue` 文件可暂留仓库，不再注册；`pages-cs/complaint/*` 保留。

### `delta-game/.../SysConfigController.java`

在 `ensureBuiltinConfigs()` 增加 5 个 `ensureConfig` 调用，`configGroup = "站点配置"`。

---

## 不变部分

- `pages/chat/room` 订单聊天（`orderId` 参数）
- `pages/chat/list` 消息列表
- `pages-cs/**` 客服工作台
- `pages-player/**` 打手端
- `api/complaint.js` 及后端投诉接口（休眠，供未来或 CS 端使用）

---

## 企微绑定操作指引（阶段二）

1. 企微管理后台 → 微信客服 → 创建客服账号 → 获取 **corpId**、**客服链接**。
2. 企微 → 客户联系 → 联系我 → 生成二维码 → 上传得 `cs.qrcode_url`。
3. 小程序管理后台 → 功能 → 客服 → 绑定企业微信客服。
4. 管理后台填写 `cs.corp_id`、`cs.service_url`，`cs.contact_mode` 改为 `auto`。

---

## 错误处理

| 情况 | 行为 |
|------|------|
| `cs.qrcode_url` 为空 | toast「客服配置中，请稍后再试」 |
| `openCustomerServiceChat` 失败 | 自动降级二维码弹窗 |
| 非 MP-WEIXIN 平台 | 直接弹二维码 |
| 复制失败 | toast「复制失败，请手动记录订单号」 |

---

## 测试计划

1. 仅配置 `qrcode_url`：首页/我的/商品详情客服、订单投诉均弹出二维码。
2. 投诉场景：点击「复制订单信息」，剪贴板内容含订单号。
3. 订单详情「聊天」仍进入与接单员的 `chat/room`。
4. 配置 `corp_id` + `service_url` + `auto`：优先打开微信客服会话。
5. API 失败时降级二维码。
6. 「我的」页无「投诉工单」入口。
7. `pages-cs` 投诉管理仍可正常访问。
