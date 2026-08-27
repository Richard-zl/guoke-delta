# 管理后台打手工作状态设计

> 日期：2026-08-27  
> 状态：待实现  
> 范围：Web 管理后台打手列表 + 订单指派 / 换人 / 接力选打手弹窗；Admin 与 CS 共用口径  
> 背景：客服无法在后台看到打手是空闲还是在接单，找人派单不便。系统没有统一工作状态字段，在线、账号状态、订单占用分散且口径不一致。

---

## 0. 决议摘要

| # | 决议 |
|---|------|
| 1 | 入口：增强现有「打手管理」列表，不新建页面 |
| 2 | 工作状态由后端实时推导，不新增 DB 状态字段、不做心跳/WebSocket |
| 3 | 列表默认筛选「可接单」；每 30 秒自动刷新当前条件 |
| 4 | 占用统计计入主接单、`player_id2`、以及 `order_player` 已接受队友；同一订单去重 |
| 5 | `ASSIGNED` 计入占用名额，避免连续指派给看似空闲的人 |
| 6 | 状态优先级：账号异常 > 离线 > 待接单确认 > 已满单 > 服务中 > 可接单 |
| 7 | 订单指派、换人、接力三处选打手弹窗复用同一套字段与禁用规则 |
| 8 | 手动指派、自动派单、打手自助接单的占用 SQL 与 `order.max_active_per_player` 缺省值统一 |

---

## 1. 背景与目标

### 现状问题

- `PlayerList.vue` 只展示账号状态（PENDING / ACTIVE / REJECTED / FROZEN），没有在线、占用、可接单。
- `Player` 表仅有 `is_online`、`status`；进行中单靠查询聚合，Admin 列表还不填 `activeOrders`。
- `selectPlayerActiveOrders` 只统计 `order.player_id`，队友和辅助打手显示为空闲。
- 占用状态集合、配置缺省值（`"1"` vs `"5"`）在指派、自动派单、打手接单三处不一致。
- 在线状态来自打手手动切换，无超时离线。

### 目标

1. 客服打开打手管理即可筛出可接单的人。
2. 每个打手只展示一个综合工作状态，并给出占用 `x/y`。
3. Admin 与 CS 列表、选打手弹窗口径一致。
4. 指派/接单上限校验与列表展示使用同一占用定义。

### 非目标

- 不改小程序客服端 UI（接口字段增强后，小程序已有的「进行中 x/y」会自动跟新口径）。
- 不引入心跳、WebSocket、或 `player.work_status` 持久化字段。
- 不改订单指派业务规则本身（仍校验在线、ACTIVE、未满额），只改展示与占用统计。

---

## 2. 状态模型

### 2.1 接口字段（挂在 `Player` 瞬态字段）

| 字段 | 含义 |
|------|------|
| `workStatus` | 综合工作状态，见 2.2 |
| `activeOrders` | 已占用名额（去重后的进行中订单数，含 `ASSIGNED`） |
| `pendingAssignedOrders` | 其中订单状态为 `ASSIGNED` 的数量 |
| `maxConcurrent` | `order.max_active_per_player`，配置缺失时默认 `1` |
| `isOnline` | 已有：1 在线 / 0 离线 |
| `lastOnlineAt` | 已有：最后切换在线时间 |

### 2.2 `workStatus` 取值与优先级

按从上到下命中第一条：

| 值 | 展示文案 | Tag | 条件 |
|----|----------|-----|------|
| `ACCOUNT_ABNORMAL` | 账号异常 | danger | `status != ACTIVE`，或 `frozenUntil` 仍在未来 |
| `OFFLINE` | 离线 | info | 账号正常且 `isOnline != 1` |
| `ASSIGNED_PENDING` | 待接单确认 | warning | `pendingAssignedOrders > 0` |
| `FULL` | 已满单 | danger | `activeOrders >= maxConcurrent` |
| `IN_SERVICE` | 服务中 | warning | `activeOrders > 0` 且仍有剩余名额 |
| `AVAILABLE` | 可接单 | success | 在线、账号正常、占用为 0 |

每个打手有且仅有一个 `workStatus`。

### 2.3 占用订单定义

进行中订单状态集合（全系统统一）：

`ASSIGNED`、`ACCEPTED`、`WAITING_TEAMMATE`、`IN_PROGRESS`

打手占用某单，当且仅当该单处于上述状态，且满足任一：

- `order.player_id = 该打手`
- `order.player_id2 = 该打手`
- 存在 `order_player`：`player_id = 该打手` 且 `status = ACCEPTED`（含主接 PRIMARY 与队友 TEAMMATE）

同一订单只计 1。`INVITED` / `EXPIRED` / `RELEASED` 不计。

`pendingAssignedOrders`：上述占用集合中 `order.status = ASSIGNED` 的数量。

### 2.4 上限校验对齐

以下路径全部改用 2.3 的占用 SQL，配置缺省一律 `"1"`：

- `OrderServiceImpl.assignOrder`
- `CsPlayerController.assign-list` / 列表 enrich
- `PlayerOrderController` 自助接单
- `UserPlayerController` 用户指定打手
- `ScheduledTaskMapper.selectAvailablePlayers`（自动派单须计入 `ASSIGNED` 与队友占用）

---

## 3. API

### 3.1 打手列表

`GET /admin/player/list`  
`GET /cs/player/list`

新增查询参数：

| 参数 | 说明 |
|------|------|
| `workStatus` | 可选。空 = 全部。打手管理页默认传 `AVAILABLE` |

返回记录补齐 2.1 全部字段。Admin 列表补齐 `activeOrders`（此前缺失）。

`workStatus` 必须在服务端过滤后再分页，禁止先分页再在内存筛，避免页码与总数错误。

实现约束：打手规模按百级考虑。允许「先按 keyword + 账号 status 查出候选 ID，批量占用后过滤，再切页」。候选过多时用占用子查询下推 SQL，不以 N+1 逐人 `COUNT`。

### 3.2 选打手列表

`GET /cs/player/assign-list`

仍只返回 `status = ACTIVE` 的打手（审核中/驳回/冻结不出现在选人弹窗）。

每条补齐 2.1 字段；顶层继续返回 `maxConcurrent`。

本接口不做 `workStatus` 默认过滤，弹窗内客服需要看到服务中/待确认的人（未满额仍可选）。

### 3.3 共用计算

抽 `PlayerWorkStatusService`（建议放 `delta-player`）：

- 批量填充占用、待确认数、`maxConcurrent`、`workStatus`
- Admin / CS 列表与 assign-list 都调用它
- 详情接口一并填充，便于打手详情与列表一致

不在 Controller 里复制优先级 if-else。

---

## 4. 前端

### 4.1 打手管理 `PlayerList.vue`

筛选：

- 保留账号状态。
- 新增工作状态下拉：全部 / 可接单 / 待接单确认 / 服务中 / 已满单 / 离线 / 账号异常。
- 默认 `workStatus = AVAILABLE`。重置后：关键词与账号状态清空，工作状态回到 `AVAILABLE`。

列（插在账号状态列之前）：

- 工作状态 Tag
- 占用：`进行中 {activeOrders}/{maxConcurrent}`；`pendingAssignedOrders > 0` 时附加「待确认 n」
- 在线 Tag + `lastOnlineAt`（无则 `-`）

行样式：`OFFLINE` 或 `ACCOUNT_ABNORMAL` 灰显。

刷新：

- 进入页面拉一次
- 停留本页时每 30 秒按当前筛选重拉
- 手动「刷新」按钮；表格旁显示最近成功更新时间（如 `14:02 更新`）
- 刷新失败：保留上次列表，`ElMessage.error`，不把表格清空
- 离开页面清除定时器

Admin 与 CS 共用该页，走各自 list 接口，字段契约相同。

### 4.2 选打手弹窗（三处一致）

覆盖：

- `OrderList.vue` 主打手 / 辅助打手指派
- `ReplaceList.vue` 指定打手
- `RelayList.vue` 指定打手

展示：工作状态、`进行中 x/y`、在线标记。

选择按钮禁用当且仅当：

- `workStatus` 为 `OFFLINE` 或 `FULL` 或 `ACCOUNT_ABNORMAL`
- 或 `activeOrders >= maxConcurrent`（`ASSIGNED_PENDING` 优先于 `FULL` 展示时，仍必须按占用上限禁用）
- 或 `isOnline !== 1`（与现有指派校验一致）

`AVAILABLE`、未满额的 `IN_SERVICE`、未满额的 `ASSIGNED_PENDING` 可选。

满单点击拦截（防御）：提示「已达最大接单数」。

离线行继续灰显。弹窗打开时拉取，不强制 30 秒轮询。

抽取共用展示/禁用辅助函数，避免三处复制文案与 Tag 颜色。

---

## 5. 错误与配置

- `order.max_active_per_player` 缺失或非数字：按 `1` 处理，列表仍可展示。
- 占用查询失败：接口返回错误，前端提示；不假装占用为 0。
- 冻结到期但 `status` 仍为 `FROZEN`：按 `ACCOUNT_ABNORMAL`（与现网解冻需人工操作一致）。

---

## 6. 验证

| 场景 | 期望 |
|------|------|
| 在线、ACTIVE、无占用 | `AVAILABLE`，出现在默认筛选 |
| 仅有 `ASSIGNED` 主单 | `ASSIGNED_PENDING`，占用含该单；达到上限时不可再派 |
| 有 `IN_PROGRESS` 且未满额 | `IN_SERVICE`，指派弹窗可选 |
| 无待确认且占用达到上限 | `FULL`，列表与弹窗均不可派 |
| 仅作为已接受队友 | 占用 +1，不再显示可接单 |
| `player_id2` 辅助 | 占用 +1 |
| 离线但账号正常 | `OFFLINE`，弹窗禁用 |
| PENDING / REJECTED / FROZEN | `ACCOUNT_ABNORMAL`；不出现在 assign-list |
| 默认可接单筛选翻页 | total 与当前页均为 `AVAILABLE` |
| Admin 与 CS 同一打手 | `workStatus` 与占用数相同 |
| 自动派单 | 不会选中已有 `ASSIGNED` 或队友占用已满的人 |
| 打手自助接单 | 占用含 `ASSIGNED` 与队友单，与列表一致 |
| 列表刷新失败 | 旧数据仍在，有错误提示 |

---

## 7. 文件落点（实现时）

后端：

- 新增工作状态计算服务
- `CrossModuleMapper`：批量占用 / 待确认查询（替换仅 `player_id` 的 COUNT）
- `AdminPlayerController` / `CsPlayerController`
- `OrderServiceImpl`、`PlayerOrderController`、`UserPlayerController`
- `ScheduledTaskMapper.selectAvailablePlayers`

前端：

- `PlayerList.vue`
- `OrderList.vue`、`ReplaceList.vue`、`RelayList.vue`
- 可选：`src/utils/playerWorkStatus.js` 文案与禁用规则
