# 打手延迟入账与提现时间窗口设计

> 日期：2026-07-31  
> 状态：待实现  
> 范围：订单确认后延迟入账、待入账展示、可配置提现时间窗口  
> 背景：当前订单确认后 `SettlementEventListener` 立即将收益写入 `player_wallet.balance`，提现全天可申请。

---

## 0. 决议摘要

| # | 决议 |
|---|---|
| 1 | 用户/客服/管理员确认订单后，打手收益延迟入账（默认 5 天，可配置） |
| 2 | 打手端展示「待入账金额」：收益页 + 提现页汇总（首版不做待入账明细列表） |
| 3 | 待入账期间投诉/退款/仲裁扣款：先扣该订单待入账，不够再扣可提现余额 |
| 4 | 延迟天数可配置（`settlement.delay_days`）；改配置只影响**新确认**订单 |
| 5 | 历史已入账订单不动，不做回溯 |
| 6 | 提现窗口默认：周二 12:00～周三 12:00、周六 12:00～周日 12:00（左闭右开），可配置 |
| 7 | 实现方案：钱包 `pendingBalance` + 订单 `settleAvailableAt` / `settled=2`，不用独立流水表 |
| 8 | 定时入账：每小时一次，带索引 + 分批 LIMIT |

---

## 1. 背景与目标

### 现状

- 确认订单发布 `OrderConfirmedEvent` → `SettlementEventListener` 立即 `creditWallet` 写入 `balance`，并记 `INCOME` 流水，`order.settled=1`。
- `PlayerWallet`：`balance` / `frozenAmount` / `totalIncome`，无待入账字段。
- 提现：`WithdrawController` 校验最低额、每日次数、冻结态；无时间窗口。前端 `withdrawRules.js` 写死「全天 24 小时可提」。

### 目标

1. 确认后收益进入「待入账」，满 N 天（默认 5）再转入可提现余额。
2. 打手在收益页、提现页看到待入账金额。
3. 提现仅在可配置的时间窗口内可申请。
4. 投诉扣款优先消耗待入账。

### 非目标

- 待入账明细列表 / 预计入账时间逐笔展示（可后续演进）。
- 可视化提现排班编辑器（沿用 `sys_config` 文本/JSON 编辑）。
- 历史已入账订单回溯为待入账。
- 节假日特殊日历。

---

## 2. 延迟入账

### 2.1 状态语义

`order.settled`：

| 值 | 含义 |
|----|------|
| `0` | 未进入结算（未确认或未跑结算） |
| `2` | 已确认并记账待入账，等待 `settleAvailableAt` |
| `1` | 已转入可提现余额 |

新增 `order.settle_available_at`：预计入账时间。

### 2.2 确认时（改 `SettlementEventListener`）

保持现有抽成、主打手/队友分成计算不变。变更点：

1. 各打手：`pendingBalance += 分成`，**不改** `balance`，**不增加** `totalIncome`（真正入账时再加）。
2. 写入 `order_player.settleAmount`（应得分成；投诉可扣减后变为「剩余待入账」）。
3. `settleAvailableAt = confirmTime + delayDays`；`delayDays` 从 `settlement.delay_days` 读取。**只持久化 `settleAvailableAt`**（确认时刻固化，不另存 delay 字段）。
4. `order.settled = 2`，`order.settleAmount = 打手总收入`（抽成后总额，与现逻辑一致）。
5. **不写** `INCOME` 交易流水。

幂等：若 `settled == 1` 或 `settled == 2` 则跳过（与现「已结算跳过」一致，扩展为待入账也不重复记账）。

### 2.3 定时入账任务

- 新任务如 `PendingSettlementTask`，`@Scheduled(cron = "0 0 * * * ?")`（每小时整点）。
- 查询：`settled = 2 AND settle_available_at <= NOW()`，`ORDER BY settle_available_at`，`LIMIT 200`（可配置常量）。
- 对每笔订单：按 `order_player`（PRIMARY + ACCEPTED TEAMMATE）将剩余 `settleAmount` 入账：
  - 若某打手 `pendingBalance < settleAmount`：整单跳过、打 error 日志告警，**不改** `settled`（下小时重试/人工排查），禁止把账做不平。
  - 否则：`pendingBalance -= amount`，`balance += amount`，`totalIncome += amount`，写 `INCOME` 流水；有现成「收入到账」通知则复用。
  - 全部打手成功后：`settled = 1`，`settleTime = now`。
- 若全部相关 `settleAmount` 已为 0（投诉扣光）：只将 `settled` 置 `1` 并写 `settleTime`，不产生流水。
- 索引：`(`settled`, `settle_available_at`)`。

### 2.4 投诉 / 仲裁扣款

现有逻辑（如 `CsComplaintController`）从打手 `balance` 扣罚/退款。改为：

1. 若订单 `settled = 2`：对该订单相关打手，先从该打手在本单的剩余 `order_player.settleAmount` 扣减，同步减少 `pendingBalance`。
2. 仍不足部分再扣 `balance`（现有路径）。
3. 若 `settled = 1`：仅扣 `balance`（与现网一致）。
4. 到期任务按剩余 `settleAmount` 入账；为 0 则只改 `settled=1`（见 §2.3）。

主打手 + 队友：按各自 `order_player` 份额独立扣减（仲裁若只罚主打手则只动 PRIMARY 行）。实现时梳理所有扣打手余额入口，优先抽成共用「先 pending 后 balance」方法。

### 2.5 展示

- `GET /player/earnings/summary` 增加 `pendingBalance`、`delayDays`（当前配置值，用于文案）。
- 提现页展示待入账（可复用 summary 或钱包字段）；旁注「确认后满 N 天转入可提现」。
- 「可提现金额」仍等于 `balance`（不含 pending）。

---

## 3. 提现时间窗口

### 3.1 默认规则

时区与订单时间一致（服务器默认，预期 `Asia/Shanghai`）。

区间 **左闭右开**：

- 周二 12:00 ≤ t ＜ 周三 12:00  
- 周六 12:00 ≤ t ＜ 周日 12:00  

### 3.2 配置

`sys_config`：

| key | 默认值 | 说明 |
|-----|--------|------|
| `settlement.delay_days` | `5` | 确认后延迟入账天数 |
| `withdraw.time_windows` | 见下 JSON | 提现窗口列表 |

```json
[
  {"startDow":2,"startTime":"12:00","endDow":3,"endTime":"12:00"},
  {"startDow":6,"startTime":"12:00","endDow":7,"endTime":"12:00"}
]
```

`startDow` / `endDow`：ISO-8601 风格 1=周一 … 7=周日。

解析失败：回退到上述默认双窗口，打 warn 日志。

### 3.3 后端校验

`WithdrawController.apply`：在最低额、日次数校验后增加窗口校验；不在窗口内抛出业务异常，文案示例：

> 当前不在提现时间。可提现时间：每周二 12:00–周三 12:00、周六 12:00–周日 12:00

只读接口 `GET /player/withdraw/window`（提现页专用，不塞进 earnings summary）：

- `inWithdrawWindow: boolean`
- `windowsText: string`（规则展示文案）
- `windows: [...]`（解析后的结构化窗口，可选）
- `nextWindowHint: string`（可选，窗外时提示下一段）

### 3.4 前端

- `pages-player/withdraw/index.vue`：展示待入账；规则文案改为接口/配置驱动；窗口外禁用提交或 toast。
- `pages-player/earnings/index.vue`：汇总增加待入账。
- `constants/withdrawRules.js`：提现时间条目改为与默认窗口一致，并注明以服务端校验为准；优先用接口返回文案覆盖写死文案。

---

## 4. 数据与迁移

Flyway 迁移建议：

1. `player_wallet` 增加 `pending_balance DECIMAL(12,2) NOT NULL DEFAULT 0`。
2. `order` 增加 `settle_available_at DATETIME NULL`；确认 `settled` 注释/文档更新为 0/1/2。
3. 索引 `idx_order_pending_settle (settled, settle_available_at)`。
4. 插入 `sys_config` 两项（若不存在）。

实体：`PlayerWallet.pendingBalance`、`Order.settleAvailableAt`。

---

## 5. 组件与改动清单

| 模块 | 改动 |
|------|------|
| `SettlementEventListener` | 写入 pending，不写 balance/INCOME；`settled=2` |
| `PendingSettlementTask`（新） | 每小时入账 |
| `ScheduledTaskMapper` 或订单/钱包 Service | 查询到期单、批量入账 |
| `CsComplaintController`（及同类扣款路径） | 先 pending 后 balance |
| `PlayerEarningsController` | summary 增加字段 |
| `WithdrawController` | 窗口校验 + 可选 window 接口 |
| 小程序打手端收益/提现页 | UI + 规则文案 |
| Flyway + sys_config | 见 §4 |

---

## 6. 错误处理与边界

| 场景 | 处理 |
|------|------|
| 重复确认/重复结算事件 | `settled∈{1,2}` 跳过 |
| pending 与 settleAmount 不一致 | 整单跳过入账 + error 日志告警，下小时重试 / 人工排查 |
| 窗口 JSON 非法 | 默认双窗口 + warn |
| 修改 `delay_days` | 仅新确认订单；已有 `settleAvailableAt` 不变 |
| 账号冻结 | 仍禁止提现（现逻辑）；待入账照常累计与到期入账 |
| 队友分成 | 与主打手同样走 pending → 定时入账 |

---

## 7. 测试要点

1. 确认订单 → `pendingBalance` 增加、`balance` 不变、无 `INCOME`；`settled=2`。
2. 将 `settle_available_at` 调到过去 → 小时任务后 → balance/totalIncome/INCOME 正确，`settled=1`，pending 减少。
3. 待入账期间仲裁：全额扣待入账；部分扣；超出待入账部分扣 balance。
4. 有队友订单：各自 pending 与入账正确。
5. 提现：窗口内成功；窗外失败；规则文案正确。
6. 历史 `settled=1` 订单不受影响。

---

## 8. 推荐实现顺序

1. DB 迁移 + 实体/配置  
2. 结算监听改为待入账  
3. 定时入账任务  
4. 投诉扣款改造  
5. earnings/withdraw API + 窗口校验  
6. 小程序 UI 与文案  

---

## 9. 开放问题（已关闭）

无。实现阶段若发现多入口扣打手余额（不止 CS 投诉），统一抽「扣款服务：先 pending 后 balance」，避免漏改。
