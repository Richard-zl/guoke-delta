# 订单「数量」与「规格」增强 设计方案

- 日期：2026-06-25
- 状态：已实现
- 涉及模块：`delta-product`、`delta-order`、`delta-user`(App商品接口)、`delta-mp`(小程序)、`delta-admin-ui`(管理后台)

## 1. 背景与目标

当前订单系统存在两个体验缺口：

1. **数量缺失**：陪玩单按小时计价，但系统无「数量」概念。用户想买 3 小时只能分 3 次下单，体验差。
2. **规格缺失**：趣味单同一商品可能有多个规格、多个价位（如「青铜局 ¥20 / 黄金局 ¥35 / 王者局 ¥60」），但商品只有单一 `price`，无法在一个商品里按规格定价。

本方案目标：

- 让指定商品支持「自定义购买数量」，一单内 `数量 × 单价` 计价，仍是一单一打手连续服务。
- 让指定商品支持「单维规格选项」，用户下单时单选其一，单价随规格变化。
- 数量与规格可同时存在：`总价 = 规格单价 × 数量`。
- 向后兼容老数据、老版本后端；对老版本小程序通过灰度策略规避风险。

## 2. 已确认的关键决策

| 编号 | 决策点 | 结论 |
|---|---|---|
| D1 | 数量的履约模型 | 仍是 **1 单 = 1 打手连续服务**，数量只影响总价与结算，**不改指派/状态机/结算逻辑** |
| D2 | 哪些商品可选数量 | **商品级开关**：每个商品配置「是否可选数量 + 单位名 + 最大数量」 |
| D3 | 数量与限购的关系 | **互斥**：开启「限购」的商品不允许开启「数量」，保存商品时校验拦截 |
| D4 | 规格模型 | **单维价格选项**：新建 `product_variant` 表，每行 `{规格名, 价格}`，用户单选其一；**不启用** `product_spec`/`price_rule` |
| D5 | 数量与规格组合 | **可共存**：规格决定单价，数量决定份数，`总价 = 规格单价 × 数量` |

## 3. 名词定义

- **规格选项 (variant)**：一个商品下的一个可售价位项，含名称与价格。商品可配 0~N 个。
  - 0 个：无规格，单价取 `product.price`（现状）。
  - ≥1 个：用户必须选一个，单价取所选 variant 的 `price`。
- **数量 (quantity)**：本单购买的份数，整数，范围 `[1, max_quantity]`。商品未开数量时恒为 1。
- **单价 (unit_price)**：下单时刻锁定的单价快照（已含规格价），用于结算与展示。

## 4. 数据模型变更

所有变更均为「新增表 / 新增列（带默认值）」，无破坏性、无需复杂迁移。

### 4.1 新增表 `product_variant`（规格选项，单维）

```sql
CREATE TABLE `product_variant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '规格选项ID',
  `product_id` bigint NOT NULL COMMENT '商品ID',
  `name` varchar(64) NOT NULL COMMENT '规格名(如:王者局)',
  `price` decimal(10,2) NOT NULL COMMENT '该规格售价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '划线原价(可空)',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品规格选项表(单维定价)';
```

### 4.2 `product` 表新增 3 列

```sql
ALTER TABLE `product` ADD COLUMN `quantity_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可选数量: 1开 0关';
ALTER TABLE `product` ADD COLUMN `unit_label` varchar(16) DEFAULT NULL COMMENT '数量单位名(如:小时/局/个)';
ALTER TABLE `product` ADD COLUMN `max_quantity` int DEFAULT NULL COMMENT '最大可购数量(quantity_enabled=1时生效)';
```

### 4.3 `order` 表新增列

```sql
ALTER TABLE `order` ADD COLUMN `quantity` int NOT NULL DEFAULT 1 COMMENT '购买数量';
ALTER TABLE `order` ADD COLUMN `unit_price` decimal(10,2) DEFAULT NULL COMMENT '下单单价快照(含规格价)';
ALTER TABLE `order` ADD COLUMN `variant_id` bigint DEFAULT NULL COMMENT '选中的规格选项ID(留痕,可空)';
ALTER TABLE `order` ADD COLUMN `variant_name` varchar(64) DEFAULT NULL COMMENT '规格名快照(可空)';
```

> 规格名单独用新列 `variant_name` 存（**不复用现有 `spec_info`**）。原因：`spec_info` 是 `json` 列，直接写纯文本会破坏列约束；`spec_info` 保持现状不动。

### 4.4 老数据回填（可选，一次性）

```sql
UPDATE `order` SET `unit_price` = `amount` WHERE `unit_price` IS NULL AND `quantity` = 1;
```

老订单 `quantity` 默认 1、`variant_id/variant_name` 为空、`unit_price` 回填为 `amount`，结算与展示逻辑不受影响。

## 5. 后端改动

### 5.1 实体

- 新增 `ProductVariant` 实体（`delta-product`，映射 `product_variant`）。
- `Product` 增持久化字段：`quantityEnabled`、`unitLabel`、`maxQuantity`；增**非持久化(瞬态)字段** `List<ProductVariant> variants`（`@TableField(exist = false)`），用于商品详情返回与保存时一并接收规格列表。
- `Order` 增字段：`quantity`、`unitPrice`、`variantId`、`variantName`。

### 5.2 DTO / VO

- `CreateOrderRequest` 增：`Long variantId`、`Integer quantity`。
- `AppProductController.detail` 返回的 `AppProductDetailVO` 中 `product` 对象自带瞬态 `variants` 与数量字段；填充逻辑：查询该商品有效 variants 并 set 到 `product.variants`。
- `ProductController.detail`（管理端 `/product/{id}`）返回 `Product` 时同样填充 `product.variants` 供后台回显。
- `ProductController.add/update` 接收的 `Product` 请求体携带 `variants`，用于一并保存规格选项（见 5.5）。

### 5.3 计价逻辑（`OrderServiceImpl.createOrder`）

替换现有「`serverPrice = product.getPrice()`」一段，改为：

```
1. 解析规格单价 unitPrice:
   - 查询该商品的有效 variants (deleted=0)
   - 若存在 variants:
       - req.variantId 必填，否则抛 "请选择规格"
       - 校验该 variant 属于本商品且未删除，否则抛 "规格不可用"
       - unitPrice = variant.price
       - variantName = variant.name
   - 若不存在 variants:
       - 忽略 req.variantId
       - unitPrice = product.price (沿用现有校验:为空抛"商品未设置价格")
   - unitPrice = normalizeMoney(unitPrice)

2. 解析数量 quantity:
   - 若 product.quantityEnabled == 1:
       - quantity = req.quantity，校验为整数且 1 <= quantity <= max(product.maxQuantity, 1)
       - 越界抛 "购买数量超出限制"
   - 否则 quantity = 1 (忽略 req.quantity)

3. 小计 subtotal = unitPrice * quantity (normalizeMoney)

4. 优惠券:
   - 门槛 coupon.minAmount 与抵扣均基于 subtotal (原逻辑里的 serverPrice 全部替换为 subtotal)
   - finalAmount = applyCoupon(subtotal)

5. 防篡改校验: req.amount == finalAmount，否则抛 "价格异常，请刷新后重试"

6. 快照写入 order:
   - order.unitPrice = unitPrice
   - order.quantity  = quantity
   - order.variantId = (有规格时) variant.id 否则 null
   - order.variantName = (有规格时) variant.name 否则 null
   - order.amount = finalAmount
```

> 体验单限购、商品限购（`ProductLimitTypeEnum`）逻辑保持不变，仍按「下单次数」计（决策 D3 下，限购商品不会开数量，无冲突）。
> 抽佣/结算公式不变：`amount × commissionRate`，`amount` 已含数量。

### 5.4 商品保存校验（`ProductServiceImpl.normalizeProduct`）

新增规则：

- 若 `quantityEnabled == 1`：
  - 与限购互斥：若 `perUserLimitType != 0`（或 `perUserLimitEnabled == 1`）→ 抛 "限购商品不可开启数量选择"。
  - `maxQuantity` 为空或 < 1 时，默认置为 24。
  - `unitLabel` 为空时默认 "份"。
- 若 `quantityEnabled == 0`：清空 `unitLabel`、`maxQuantity`。

### 5.5 规格选项的保存

采用「随商品保存」方式，保证原子性：

- `ProductController.add/update` 接收 `Product`（含瞬态 `variants`）。
- 保存商品后，对该商品的 `product_variant` 执行「全量覆盖」：先逻辑删除该商品所有旧 variant，再插入提交的 variant 列表（`price >= 0`、`name` 非空校验）。
- App 端 `/app/product/{id}` 与管理端 `/product/{id}` 返回时带出有效 variants 列表。

## 6. 小程序改动 (`delta-mp`)

### 6.1 商品详情页 `pages/product/detail.vue`

- 拉取 `/app/product/{id}`，读取 `variants` 与商品的 `quantityEnabled/unitLabel/maxQuantity`。
- 若 `variants` 非空：展示规格单选区，默认选中第一项；当前展示价 = 选中 variant 价。
- 若 `quantityEnabled`：展示数量步进器（`[1, maxQuantity]`），单位名用 `unitLabel`。
- 实时展示价 = `选中规格价(或product.price) × 数量`。
- 「立即购买」跳转下单页时带上：`variantId`、`variantName`、`unitPrice`、`quantity`。

### 6.2 下单页 `pages/order/create.vue`

- 接收并展示「规格名 + 单价 × 数量 = 小计」。
- `amount`（用于优惠券和提交）= 小计；优惠券作用于小计。
- `submitOrder` 的 `orderData` 增加 `variantId`、`quantity`。
- 体验单等无规格无数量的商品：行为与现状一致（`quantity=1`、不传 `variantId`）。

### 6.3 订单详情 / 订单卡片展示

- `pages/order/detail.vue`、`pages-player/...`、`pages-cs/...`、`components/OrderCard.vue`：在商品信息处展示规格名（`variantName`）与「`× 数量 单位名`」、单价。
- 无规格无数量的老订单不展示这些字段（值为空/1），保持原样。

## 7. 管理后台改动 (`delta-admin-ui`)

### 7.1 商品表单 `views/product/ProductForm.vue`

- 「价格与规则」区新增：
  - 规格选项编辑器：可增删行，每行「规格名 + 价格(+ 选填原价)」。说明：配了规格后，用户下单按规格价，`商品价格` 作为无规格时的兜底/默认展示。
  - 「可选数量」开关 + 「单位名」输入 + 「最大数量」输入（开关开启时显示）。
- 前端校验与提示：
  - 「可选数量」与「限购周期 ≠ 不限购」互斥，二者只能其一（选了其一则禁用/提示另一项）。
  - 提交时把 `variants` 一并放入商品对象提交给 `/product`。
- `loadDetail` 回显 `variants`、`quantityEnabled/unitLabel/maxQuantity`。

### 7.2 订单列表/详情 `views/order/OrderList.vue`

- 展示列/详情补充：规格名、数量、单价。

## 8. 兼容性与灰度策略

| 对象 | 兼容情况与处理 |
|---|---|
| 老订单数据 | 完全兼容：`quantity=1`、`variant_id/variant_name` 空、`unit_price` 回填=amount，结算/状态机不变 |
| 老版本后端 jar | 兼容：旧实体无新字段，新列读写时被忽略并保留原值 |
| 老版本小程序 | 有条件兼容：老 App 不传 `variantId/quantity`，对**未配置规格/数量的商品**照常工作 |

**灰度纪律（必须遵守）**：

- 先发布后端 + 数据库 + 新版小程序，**待新版小程序铺开后**，再在后台给商品配置规格/数量。
- 后端兜底规则：商品**已配置 variants** 但下单请求未带 `variantId` → 直接拒绝（"请选择规格"）。即老 App 无法对已配规格的商品下单，避免错价。这是刻意取舍，配合上面的灰度顺序即可规避。

## 9. 风险与对策

| 风险 | 对策 |
|---|---|
| `spec_info` 为 JSON 列，写纯文本会破坏列 | 规格名改用新列 `variant_name`，`spec_info` 不动 |
| 老 App 对已配规格商品错价/缺规格 | 灰度顺序 + 后端「有规格必传 variantId」拒绝兜底（见第 8 节） |
| 优惠券门槛改为基于小计(单价×数量)，行为变化 | 属预期变化，需与运营对齐（满减更易达门槛） |
| 数量×单价金额计算与前端不一致 | 服务端重算并做 `amount == finalAmount` 防篡改校验（沿用现有机制） |
| 限购与数量同时开启导致语义冲突 | 商品保存时强校验互斥（5.4） |
| 指派/结算受影响 | 无影响：一单一打手、`amount` 已含数量，零改动 |

## 10. 验收标准

1. 商品可配 0/N 个规格选项；配 ≥1 个时用户端必须选规格才能下单，单价随规格变化。
2. 商品可开启数量；用户端可在 `[1, maxQuantity]` 选数量，总价 = 单价 × 数量。
3. 规格 + 数量可同时生效，总价 = 规格价 × 数量；优惠券作用于小计。
4. 限购商品无法开启数量（后台保存被拦截）。
5. 订单详情（用户/打手/客服/后台）正确展示规格名、数量、单价。
6. 老订单、老商品零改动可正常浏览与结算。
7. 服务端价格防篡改校验对「规格 + 数量 + 优惠券」组合生效。

## 11. 不做的事 (YAGNI)

- 不做多维 SKU 组合定价（不启用 `product_spec`/`price_rule`）。
- 数量不拆分给多打手、不改结算分账模型。
- 不为限购商品支持数量。
- 不改动现有订单状态机与指派并发限制。
