# 收益日报双口径设计

> 日期：2026-08-03  
> 状态：待实现  
> 范围：`GET /admin/statistics/income-daily` + Admin UI「收益日报」  
> 背景：延迟入账上线后，确认订单先为 `settled=2` 且无 `settle_time`；现日报仅统计 `settled=1` 且按 `settle_time` 归天，导致近期（如八月）确认业务在日报中显示为 0。

---

## 0. 决议摘要

| # | 决议 |
|---|------|
| 1 | 每日一行展示**两套数**：确认口径 + 已入账口径 |
| 2 | 确认口径：`status IN ('CONFIRMED','REVIEWED')` 且 `settled IN (1,2)`，按 **`confirm_time`** 归天 |
| 3 | 已入账口径：保持现状 `settled = 1` 且 `settle_time IS NOT NULL`，按 **`settle_time`** 归天 |
| 4 | 顶部汇总：确认合计 4 卡 + 已入账合计 4 卡 |
| 5 | 展开明细：当日「确认订单」与「入账订单」两段列表 |
| 6 | 旧字段 `orderCount` / `orderAmount` / `playerIncome` / `commissionIncome` 兼容映射为**确认**口径 |
| 7 | 实现：扩展 `StatsMapper` 两套聚合/明细 SQL + `AdminStatisticsController` 拼行；改 `IncomeDaily.vue` |

---

## 1. 背景与目标

### 现状

- SQL 仅：`settled = 1 AND settle_time IS NOT NULL AND status IN ('CONFIRMED','REVIEWED')`，按 `settle_time`。
- 延迟入账：确认时 `settled=2`，写 `settle_amount` / `settle_available_at`，**不写** `settle_time`；到期释放才 `settled=1` 并写 `settle_time=now()`。
- 结果：新确认订单在延迟期内不进日报。

### 目标

1. 经营侧能按**确认日**看到有效订单金额与抽成（含待入账）。
2. 资金侧仍能按**入账日**看到真正到账金额。
3. 同屏对比，避免单一数字歧义。

### 非目标

- 不改仪表盘 GMV 口径。
- 不改延迟入账业务逻辑本身。
- 不按支付日 / 下单日做第三套口径。

---

## 2. 指标定义

金额字段统一：

- 订单金额：`SUM(amount)`
- 打手收入：`SUM(settle_amount)`（确认时已写入，待入账订单也有值）
- 平台抽成：`SUM(amount - COALESCE(settle_amount, 0))`

### 2.1 确认口径（confirm*）

| 条件 | 值 |
|------|-----|
| 状态 | `CONFIRMED` / `REVIEWED` |
| 结算标记 | `settled IN (1, 2)` |
| 时间 | `confirm_time IS NOT NULL` 且落在查询窗口 |
| 归天 | `DATE(confirm_time)` |

说明：`settled=0` 未进入结算链路的不纳入；历史异常无 `confirm_time` 的不纳入。

### 2.2 已入账口径（settled*）

| 条件 | 值 |
|------|-----|
| 状态 | `CONFIRMED` / `REVIEWED` |
| 结算标记 | `settled = 1` |
| 时间 | `settle_time IS NOT NULL` 且落在查询窗口 |
| 归天 | `DATE(settle_time)` |

与当前线上逻辑一致。

### 2.3 关系说明

- 同一订单可在**确认日**进入 confirm*，在 **N 天后入账日**进入 settled*（两天各记一次，不是重复错误）。
- 同一天 confirm 合计与 settled 合计**不必相等**（跨日延迟入账）。

---

## 3. API

路径不变：`GET /admin/statistics/income-daily?startDate=&endDate=`

时间窗口：`[startDate 00:00:00, endDate+1 00:00:00)`（与现状一致）。

### 3.1 `list[]` 每日项

| 字段 | 含义 |
|------|------|
| `statDate` | `yyyy-MM-dd` |
| `confirmOrderCount` / `confirmOrderAmount` / `confirmPlayerIncome` / `confirmCommissionIncome` | 确认口径 |
| `settledOrderCount` / `settledOrderAmount` / `settledPlayerIncome` / `settledCommissionIncome` | 已入账口径 |
| `orderCount` / `orderAmount` / `playerIncome` / `commissionIncome` | **兼容 = 确认口径** |
| `confirmOrders` | 当日确认明细列表 |
| `settledOrders` | 当日入账明细列表 |
| `orders` | **兼容 = `confirmOrders`** |

明细行字段沿用现有：`id, orderNo, productName, amount, playerIncome, commissionIncome, createdAt, settleTime, userNickname, playerNickname`；确认明细增加可选 `settled`（1/2），待入账时 `settleTime` 可为空。

### 3.2 `summary`

| 字段 | 含义 |
|------|------|
| `confirmOrderCount` 等四项 | 区间确认合计 |
| `settledOrderCount` 等四项 | 区间已入账合计 |
| `orderCount` 等四项 | **兼容 = 确认合计** |

### 3.3 Mapper

在 `StatsMapper` 新增（或并行保留旧方法后切换调用）：

1. `incomeDailyConfirmStatsByRange` — 按 `DATE(confirm_time)` 聚合，`settled IN (1,2)`  
2. `incomeDailyConfirmOrderDetailsByRange` — 确认明细  
3. 已入账继续用现有 `incomeDailyStatsByRange` / `incomeDailyOrderDetailsByRange`（或重命名为 settled*，行为不变）

Controller 按日填零拼行；`statDate` 键需规范为 `yyyy-MM-dd`（与仪表盘 GMV 同样注意 `DATE()` 返回类型）。

---

## 4. UI（`IncomeDaily.vue`）

### 4.1 顶部汇总

两行各 4 卡：

1. 确认：有效订单数 / 有效订单金额 / 打手收入 / 平台抽成（标签注明「按确认日，含待入账」）  
2. 已入账：同上四项（标签注明「按入账日」）

### 4.2 每日表

列：日期 | 确认订单数 | 确认金额 | 确认抽成 | 已入账订单数 | 已入账金额 | 已入账抽成  
（打手收入两列可一并展示，若过宽可用分组表头「确认 / 已入账」。）

### 4.3 展开

- 「确认订单」表：含待入账标签；结算时间空则显示「待入账」  
- 「入账订单」表：结算时间有值  

### 4.4 文案

表头/卡片旁短 tip，避免与仪表盘 GMV 混淆：本页是**结算/抽成**视角，不是支付 GMV。

---

## 5. 测试计划

1. 仅 `settled=2`、确认日在八月：确认侧有数，入账侧为 0。  
2. 释放入账后：确认日仍在确认侧；入账日（释放日）出现在 settled*。  
3. 延迟入账上线前历史 `settled=1`：两边在对应日期均可统计（确认按 confirm_time，入账按 settle_time；旧数据两者常接近）。  
4. 区间汇总 = 每日相加。  
5. 兼容字段与 confirm* 一致。  
6. 无 `confirm_time` 的异常单不进确认侧。

---

## 6. 风险

| 风险 | 缓解 |
|------|------|
| 运营把「确认」当成「已到账」 | UI 明确「含待入账」 |
| 同单跨日出现在两套明细 | 文档与 tip 说明属预期 |
| `DATE(confirm_time)` 键格式 | Controller 归一化为 `yyyy-MM-dd` |
