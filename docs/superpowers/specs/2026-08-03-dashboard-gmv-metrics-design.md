# 仪表盘经营指标（GMV）口径设计

> 日期：2026-08-03  
> 状态：待实现  
> 范围：Web 管理员仪表盘 + Web 客服工作台的经营金额/订单数口径统一  
> 背景：当前「成交额 / 订单数 / 近 7 天趋势」过滤条件不一致；成交额含退款单且按 `created_at`；订单数含待支付与未付款取消。无法准确反映经营 GMV。

---

## 0. 决议摘要

| # | 决议 |
|---|------|
| 1 | 金额权威数据源为 `payment`（`biz_type = 'ORDER'`），不再用 `order.amount` + 状态黑名单凑 GMV |
| 2 | 展示三卡：**GMV（已扣同日退款）**、**退款额**、**净成交**；字段旁备注说明口径 |
| 3 | GMV 只扣除「支付日 = 统计日且退款日 = 统计日」的同日退款；跨日退款不进 GMV，只进「退款额」 |
| 4 | 已支付订单数与 GMV 同源：按 `paid_at`，含后来退款的订单；不含待支付、未付款取消 |
| 5 | 时间归属：支付相关按 `paid_at`；退款相关按 `refund_time` |
| 6 | 部分退款按 `refund_amount` 实退金额 |
| 7 | 覆盖范围：今日 + 累计 + 近 7 天趋势；Admin Web + CS Web；小程序 CS、收益日报本期不动 |
| 8 | 实现方案：抽 `DashboardMetricsService`（delta-common），Admin/CS 共用 |

---

## 1. 背景与目标

### 现状问题

| 指标 | 当前行为 | 问题 |
|------|----------|------|
| 今日/总成交额 | `order.amount`，排除 `CANCELLED`/`PENDING_PAYMENT`，按 `created_at` | 仍含退款单金额；非支付日归属 |
| 今日/总订单数 | 全状态计数 | 含待支付、未付款取消 |
| 近 7 天趋势金额 | 无状态过滤 | 与顶部卡片不一致 |
| 收益日报 | 已结算的 `CONFIRMED`/`REVIEWED` | 财务口径，与仪表盘经营口径不同（保留） |

订单状态事实（与本指标相关）：

- `CANCELLED`：仅来自未支付（用户取消 / 超时），**未付过款**
- 已支付后用户取消：走 `REFUNDING` → `REFUNDED`，**不会**变成 `CANCELLED`

### 目标

1. 仪表盘经营数字真实反映「付了多少 / 退了多少 / 净多少」
2. Admin 与 CS 口径一致
3. 今日、累计、近 7 天趋势同一套公式
4. 每个金额字段有清晰备注，避免歧义

### 非目标

- 不改收益日报（结算/抽成口径）
- 不改小程序客服工作台
- 不引入日汇总物化表
- 不改造 `statistics_daily` 批任务（可后续对齐）

---

## 2. 指标定义

数据源：`payment`，过滤 `biz_type = 'ORDER'`。

### 2.1 中间量（可只在服务层使用）

对统计日 `D`（或时间窗口 `W`）：

| 符号 | 定义 |
|------|------|
| `paidGross(D)` | `SUM(amount)`，`DATE(paid_at) = D` 且 `paid_at IS NOT NULL` |
| `sameDayRefund(D)` | `SUM(refund_amount)`，`refund_time` 非空且 `DATE(paid_at) = DATE(refund_time) = D` |
| `refundTotal(D)` | `SUM(refund_amount)`，`DATE(refund_time) = D` |
| `crossDayRefund(D)` | `refundTotal(D) - sameDayRefund(D)` |
| `paidOrderCount(D)` | `COUNT(DISTINCT order_id)`，`DATE(paid_at) = D` |

累计 / 区间：将 `DATE(...) = D` 换成落在窗口内；同日退款条件为「支付日与退款日为同一天，且该天落在窗口内」（按天累加同日退款，或等价 SQL）。

近 7 天趋势：按天输出上述指标。

### 2.2 展示指标

| 展示名 | 公式 | 备注文案（前端 tooltip） |
|--------|------|--------------------------|
| GMV（已扣同日退款） | `paidGross - sameDayRefund` | 支付成功金额（按支付日），已扣除支付日与退款日均为该统计日的退款；不含待支付/未付款取消；不扣跨日退款 |
| 退款额 | `refundTotal` | 统计日内完成退款的金额（可含历史支付单） |
| 净成交 | `paidGross - refundTotal`（= GMV − 跨日退款） | 支付毛额减去当日全部退款；因跨日退款可能小于 GMV，甚至为负 |
| 已支付订单数 | `paidOrderCount` | 支付成功过的订单数（含后来全额/部分退款的） |

### 2.3 恒等式（验收用）

```
净成交 = paidGross - refundTotal
净成交 = GMV - crossDayRefund
GMV = paidGross - sameDayRefund
```

### 2.4 示例

| 事件 | 金额 |
|------|------|
| 今天支付 | 1000 |
| 其中今天退掉 | 100 |
| 昨天支付的订单今天退 | 200 |

结果：

- GMV = 1000 − 100 = **900**
- 退款额 = 100 + 200 = **300**
- 净成交 = 1000 − 300 = **700**

### 2.5 边界

| 场景 | 处理 |
|------|------|
| `CANCELLED`（未付款） | 无成功支付记录 → 不进任何金额与已支付订单数 |
| `REFUNDED` | 支付日计入 `paidGross`；退款日计入 `refundTotal`；若同日则进入 `sameDayRefund` |
| `REFUNDING`（退款未完成） | `refund_time` 为空 → 不进退款额；支付成功则仍进 `paidGross` |
| 部分退款 | 按 `refund_amount` |
| 打手押金 `PLAYER_DEPOSIT` | 排除 |
| 时区 | 沿用 MySQL `DATE(...)` / 服务器时区，本期不做额外转换 |

---

## 3. 架构

```
AdminHomeController ──┐
                      ├── DashboardMetricsService ── Payment 聚合 SQL（StatsMapper 或专用 Mapper）
CsDashboardController ┘
```

- 新建 `DashboardMetricsService`（`delta-common`）：封装日/累计/趋势查询与派生字段
- SQL 可落在扩展后的 `StatsMapper` 或新建 `DashboardMetricsMapper`；禁止在 Controller 内拼口径
- DTO 建议字段：`paidOrderCount`、`gmv`、`refundAmount`、`netAmount`（及昨日对比所需镜像字段）
- 旧字段 `todayAmount` / `totalAmount`：实现期可映射为 `gmv` 做短暂兼容，前端尽快改读新字段；文档与 UI 文案以新语义为准

---

## 4. API 与 UI

### 4.1 API

- `GET /admin/home/dashboard`：今日 / 昨日对比 / 累计 / `orderTrend` 改用新服务
- `GET /cs/dashboard`：成交相关字段对齐同一服务

趋势项每行至少包含：`date`、`paidOrderCount`（或 `orders` 语义升级为已支付）、`gmv`、`refundAmount`、`netAmount`。

### 4.2 Admin UI（`delta-admin-ui/src/views/dashboard/index.vue`）

**今日数据**

- 「今日订单」→「今日已支付订单」+ tooltip
- 「今日成交额」→ 三卡：GMV / 退款额 / 净成交（标题含「已扣同日退款」说明 + tooltip）
- 新增用户、新增打手保留；布局可换行

**累计数据**

- 「总订单数」→「累计已支付订单」
- 「总成交额」→ 累计 GMV / 累计退款额 / 累计净成交

**近 7 天趋势**

- 列：日期 | 已支付订单数 | GMV | 退款额 | 净成交

**客服 Web**

- 「今日成交额」改为同口径三卡（或空间不足时一行三数 + tooltip）

**不动**

- 订单状态分布、用户/打手副统计卡、收益日报页

---

## 5. 测试计划

1. **未付款取消**：仅 `CANCELLED`，无 payment → 订单数/金额均为 0 增量  
2. **支付未退**：计入 GMV、已支付订单、净成交；退款额 0  
3. **同日全额退**：GMV 不增加（或增加后扣回为 0）；退款额 = 支付额；净成交 0  
4. **同日部分退**：GMV = 支付 − 实退；退款额 = 实退；净成交 = GMV  
5. **跨日退**：支付日记入 GMV；退款日记入退款额，不改支付日 GMV；退款日净成交减少  
6. **押金支付**：不计入  
7. **Admin 与 CS**：同一自然日金额一致  
8. **恒等式**：抽样日校验 §2.3

---

## 6. 风险与后续

| 风险 | 缓解 |
|------|------|
| 历史 `payment.paid_at` 为空 | 实现前抽查；必要时回填或回退用支付成功状态时间 |
| 微信退款异步导致短暂 `REFUNDING` | 以 `refund_time` 写入为准；未完成不进退款额 |
| 数字相对旧仪表盘下降 | 预期行为（剔除未付取消噪声、扣同日退款）；上线说明口径变更 |

后续可选：小程序 CS 对齐；`statistics_daily` 与本口径统一；付费用户占比改用 payment。
