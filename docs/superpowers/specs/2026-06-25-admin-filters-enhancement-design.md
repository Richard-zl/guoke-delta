# 管理端筛选增强 — 执行计划

> 适用范围：delta-admin-ui 订单管理 / 用户管理；后端 Admin + CS 双端
> 核心约束：零数据库结构变更、零数据迁移、向后完全兼容
> 状态：已实现

## 0. 不可违反的原则

1. 不新增/修改/删除任何数据库列，不做数据迁移。
2. 筛选一律使用 ID，名称只用于展示。
3. 打手筛选必须同时匹配主接与辅助：`player_id = ? OR player_id2 = ?`。
4. 商品筛选用 `product_id`，列表仍显示 `product_name` 下单快照。
5. 远程搜索代替全量下拉，规避改名/下架导致选项缺失。
6. Admin 与 CS 后端参数契约必须一致，前端一套 UI 两用。
7. `product_id` 索引为可选优化，**本次不做、不写迁移文件**。

## 1. 后端 — 订单列表查询增强

文件：
- `delta-game/delta-admin/.../controller/AdminOrderController.java`
- `delta-game/delta-cs/.../controller/CsOrderController.java`
- 建议在 delta-order 模块抽公共方法（`OrderService` 或新建 `OrderQueryBuilder`），两端复用，避免逻辑分叉。

新增/调整查询参数（全部可选，不传则不生效，保证兼容旧调用）：

| 参数 | 类型 | 逻辑 |
|------|------|------|
| orderNo | String | like，保留 |
| status | String | eq，保留 |
| statusIn | String | 逗号分隔，in(...)，用于快捷筛选 |
| userId | Long | eq(user_id) |
| playerId | Long | and(player_id = ? OR player_id2 = ?) ← 核心 |
| productId | Long | eq(product_id) |
| createdAtStart | String(yyyy-MM-dd HH:mm:ss) | created_at >= ? |
| createdAtEnd | String | created_at <= ? |
| unassigned | Boolean | true → status=PAID AND player_id IS NULL |

打手 OR 匹配实现：

```java
if (playerId != null) {
    w.and(q -> q.eq(Order::getPlayerId, playerId)
                .or()
                .eq(Order::getPlayerId2, playerId));
}
```

- 时间参数为字符串时在 Controller 解析为 LocalDateTime；为空跳过。
- status 与 statusIn 同时存在时以 status 优先（前端保证互斥）。

不做：`order_player` 队友角色匹配（二期再议）。

## 2. 后端 — 用户列表查询增强

文件：
- `delta-game/delta-admin/.../controller/AdminUserController.java`
- `delta-game/delta-cs/.../controller/CsUserController.java`（与 Admin 对齐 status 等）

| 参数 | 逻辑 |
|------|------|
| keyword | and(nickname like ? OR phone like ?) ← 修复：现仅搜 nickname |
| status | eq，保留 |
| levelCode | eq(level_code)，值域 BRONZE/SILVER/GOLD/DIAMOND/KING |
| createdAtStart / createdAtEnd | created_at 范围 |
| userId | 可选精确匹配（远程搜索选中后传入） |

## 3. 前端 — 订单管理 OrderList.vue

3.1 query 扩展：

```js
{ pageNum, pageSize, orderNo, status, statusIn, userId, playerId,
  productId, createdAtStart, createdAtEnd, unassigned }
```

重置时清空所有字段及远程搜索 label 缓存。

3.2 筛选区布局：
- 第一行：订单号 / 状态 / 下单时间范围 / 用户🔍 / 打手🔍 / 商品🔍 / 查询 / 重置
- 快捷行：待指派 | 进行中 | 售后相关

快捷映射：
- 待指派 → unassigned=true
- 进行中 → statusIn=ASSIGNED,ACCEPTED,WAITING_TEAMMATE,IN_PROGRESS
- 售后相关 → statusIn=REFUNDING,REFUNDED,DISPUTED,ARBITRATED

3.3 远程搜索数据源（复用现有 API）：
- 用户：adminUserList / csUserList，keyword 搜昵称/手机号 → 传 userId
- 打手：playerAssignList（已有） → 传 playerId
- 商品：getProductList，keyword 搜名称，需能搜到下架商品 → 传 productId

选项展示：
- 用户：昵称 (手机号) / ID: xxx
- 打手：昵称 (ID: xxx)
- 商品：名称 (ID: xxx)，下架追加 (已下架)

控件：el-select + remote + filterable，交互对齐现有指派弹窗。

3.4 Admin/CS 双端：沿用 isAdmin 切换 API，后端契约一致即可。

## 4. 前端 — 用户管理 UserList.vue

- 筛选区：昵称/手机号 / 状态 / 会员等级▼ / 注册时间范围 / 查询 / 重置
- 等级下拉传 levelCode：BRONZE/SILVER/GOLD/DIAMOND/KING。
- 可选：userId 精确输入。

## 5. 可复用组件（可选）

抽 RemoteEntitySelect.vue：props = fetchFn/labelKey/valueKey/placeholder，支持按 ID 回显。
工期紧可先在 OrderList.vue 内联，后续再抽。

## 6. 验收清单

订单：
- [ ] 按用户 ID 查全部订单
- [ ] 打手作为主接的订单可查到
- [ ] 打手仅作为辅助的订单也可查到（核心）
- [ ] 按商品 ID 筛选正确，下架商品可搜到并筛历史订单
- [ ] 时间范围首尾边界正确
- [ ] 待指派只出 PAID 且未指派主接
- [ ] 改名后按 ID 筛选结果不变；商品名显快照、打手名显最新
- [ ] Admin / CS 行为一致
- [ ] 组合筛选（时间+打手+状态）正确
- [ ] 旧的不带新参数的请求仍正常返回（兼容）

用户：
- [ ] 手机号可搜到（修复后）
- [ ] 等级/注册时间/状态组合正确
- [ ] CS 端用户列表 status 筛选可用

兼容性：
- [ ] 未执行任何 DB 迁移，老库直接可用
- [ ] 历史订单（player_id2 为 NULL）筛选无误判

## 7. 实施顺序

1. 后端订单：公共 Builder + 打手 OR + 新参数（Admin/CS）
2. 后端用户：keyword 修复 + levelCode + 时间
3. 前端 OrderList.vue：query + 筛选 UI + 远程搜索 + 快捷
4. 前端 UserList.vue：筛选 UI
5. 联调 + 按验收清单回归

## 8. 明确不做（二期）

`order_player` 队友匹配、金额/结算状态、商品分类筛订单、余额/积分/订单数区间、统一大搜索、`product_id` 索引迁移。
