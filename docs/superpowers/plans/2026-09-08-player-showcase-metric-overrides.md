# 打手风采展示指标覆盖 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 允许管理员覆盖风采页的评分、完成单和完成率，同时保持其他模块的真实业务数据不变。

**Architecture:** 三个可空覆盖字段存入 `player_showcase`。风采服务组装公开 DTO 时执行“覆盖值优先、真实值兜底”；管理后台同时展示真实值与覆盖输入。公共接口字段名保持不变。

**Tech Stack:** MySQL 8、Spring Boot、MyBatis-Plus、JUnit 5、Vue 3、Element Plus。

## Global Constraints

- 覆盖值只影响风采公开列表和详情。
- 在线、满载、占用和 `canDesignate` 必须继续使用真实数据。
- 空覆盖值必须回退真实值。
- 不修改 `player`、订单和评价数据。
- 不创建 Git commit。

---

### Task 1: 数据库字段

**Files:**
- Modify: `sql/player_showcase.sql`
- Modify: `delta_game.sql`

**Interfaces:**
- Produces: `player_showcase.display_rating decimal(3,2) NULL`
- Produces: `player_showcase.display_completed_orders int NULL`
- Produces: `player_showcase.display_complete_rate decimal(5,2) NULL`

- [ ] **Step 1: 在增量 SQL 添加字段**

```sql
ALTER TABLE `player_showcase`
  ADD COLUMN `display_rating` decimal(3,2) NULL COMMENT '风采展示评分，空则使用真实值' AFTER `bio`,
  ADD COLUMN `display_completed_orders` int NULL COMMENT '风采展示完成单，空则使用真实值' AFTER `display_rating`,
  ADD COLUMN `display_complete_rate` decimal(5,2) NULL COMMENT '风采展示完成率，空则使用真实值' AFTER `display_completed_orders`;
```

- [ ] **Step 2: 同步全库结构**

在 `delta_game.sql` 的 `player_showcase` 建表语句中加入相同三列，确保新库初始化和存量库升级结构一致。

- [ ] **Step 3: 验证 SQL**

```sql
SHOW COLUMNS FROM player_showcase LIKE 'display_%';
```

预期：返回 3 行，且 `Null` 都是 `YES`。

---

### Task 2: 后端校验与覆盖解析

**Files:**
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/entity/PlayerShowcase.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/PlayerShowcaseRules.java`
- Modify: `delta-game/delta-player/src/test/java/com/delta/player/service/PlayerShowcaseRulesTest.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerShowcaseServiceImpl.java`
- Modify: `delta-game/delta-admin/src/main/java/com/delta/admin/controller/AdminPlayerShowcaseController.java`

**Interfaces:**
- Produces: `PlayerShowcase.displayRating`
- Produces: `PlayerShowcase.displayCompletedOrders`
- Produces: `PlayerShowcase.displayCompleteRate`
- Produces: `PlayerShowcaseRules.metricBlockReason(BigDecimal, Integer, BigDecimal)`

- [ ] **Step 1: 先写失败单测**

在 `PlayerShowcaseRulesTest` 增加：

```java
@Test
void validatesShowcaseDisplayMetrics() {
    assertNull(PlayerShowcaseRules.metricBlockReason(null, null, null));
    assertNull(PlayerShowcaseRules.metricBlockReason(
            new BigDecimal("4.90"), 120, new BigDecimal("98.50")));
    assertEquals("展示评分应在1到5之间",
            PlayerShowcaseRules.metricBlockReason(new BigDecimal("5.01"), null, null));
    assertEquals("展示完成单不能小于0",
            PlayerShowcaseRules.metricBlockReason(null, -1, null));
    assertEquals("展示完成率应在0到100之间",
            PlayerShowcaseRules.metricBlockReason(null, null, new BigDecimal("100.01")));
}
```

- [ ] **Step 2: 运行单测并确认失败**

```bash
cd delta-game
mvn -pl delta-player -am test -Dtest=PlayerShowcaseRulesTest -Dsurefire.failIfNoSpecifiedTests=false
```

预期：因 `metricBlockReason` 尚不存在而编译失败。

- [ ] **Step 3: 增加实体字段及纯规则校验**

```java
private BigDecimal displayRating;
private Integer displayCompletedOrders;
private BigDecimal displayCompleteRate;
```

规则返回第一条错误；评分范围 1～5、完成单不小于 0、完成率范围 0～100，空值直接放行。

- [ ] **Step 4: 保存时执行后端校验**

在 `saveCard` 中调用：

```java
String metricReason = PlayerShowcaseRules.metricBlockReason(
        card.getDisplayRating(),
        card.getDisplayCompletedOrders(),
        card.getDisplayCompleteRate());
if (metricReason != null) {
    throw new BusinessException(metricReason);
}
```

- [ ] **Step 5: 公开 DTO 使用覆盖值**

在 `fillCard` 中替换三个赋值：

```java
vo.setAvgRating(card.getDisplayRating() != null
        ? card.getDisplayRating() : player.getAvgRating());
vo.setCompletedOrders(card.getDisplayCompletedOrders() != null
        ? card.getDisplayCompletedOrders() : completed);
vo.setCompleteRate(card.getDisplayCompleteRate() != null
        ? card.getDisplayCompleteRate() : player.getCompleteRate());
```

保留真实的 `activeOrders`、`isOnline`、`maxConcurrent` 和 `canDesignate`。

- [ ] **Step 6: 后台列表同时返回真实值**

`AdminPlayerShowcaseController.toRow` 返回：

```java
row.put("displayRating", card.getDisplayRating());
row.put("displayCompletedOrders", card.getDisplayCompletedOrders());
row.put("displayCompleteRate", card.getDisplayCompleteRate());
row.put("realAvgRating", player.getAvgRating());
row.put("realCompletedOrders",
        crossModuleMapper.selectPlayerCompletedOrders(player.getId()));
row.put("realCompleteRate", player.getCompleteRate());
```

为控制器注入 `CrossModuleMapper`。

- [ ] **Step 7: 运行测试**

```bash
cd delta-game
mvn -pl delta-player -am test -Dtest=PlayerShowcaseRulesTest -Dsurefire.failIfNoSpecifiedTests=false
```

预期：全部通过。

---

### Task 3: 管理后台编辑展示值

**Files:**
- Modify: `delta-admin-ui/src/views/player/PlayerShowcase.vue`

**Interfaces:**
- Consumes: 后台风采行中的 `real*` 与 `display*` 字段
- Produces: 保存请求中的 `displayRating`、`displayCompletedOrders`、`displayCompleteRate`

- [ ] **Step 1: 扩展表单状态**

```js
const form = reactive({
  playerId: null,
  coverUrl: '',
  tagline: '',
  bio: '',
  sortOrder: 0,
  selectedImages: '',
  status: 0,
  displayRating: null,
  displayCompletedOrders: null,
  displayCompleteRate: null
})
```

- [ ] **Step 2: 增加展示数据编辑区**

每项采用 `el-input-number`：

```vue
<el-divider content-position="left">风采展示数据</el-divider>
<el-alert title="留空使用真实数据，仅影响风采页展示" type="info" :closable="false" />
```

- 评分：`min=1`、`max=5`、`precision=2`、`step=0.1`
- 完成单：`min=0`、`precision=0`
- 完成率：`min=0`、`max=100`、`precision=2`
- 每项旁边显示真实值，并提供“恢复真实值”按钮，将对应覆盖字段设为 `null`

- [ ] **Step 3: 编辑回填并提交**

打开弹窗时回填三个 `display*` 字段；构造 payload 时原样发送。不能用 `||` 转换，否则 `0` 和 `null` 会混淆。

- [ ] **Step 4: 构建后台**

```bash
cd delta-admin-ui
npm run build
```

预期：Vite 构建成功，无新增编译错误。

---

### Task 4: 集成验收

**Files:**
- Verify: `delta-mp/pages/index/index.vue`
- Verify: `delta-mp/pages/showcase/list.vue`
- Verify: `delta-mp/pages/showcase/detail.vue`

- [ ] **Step 1: 执行存量库迁移**

执行 Task 1 的 `ALTER TABLE`，重启后端。

- [ ] **Step 2: 验证真实值回退**

清空三个覆盖值，调用：

```text
GET /player/showcase/active
GET /player/showcase/detail/{playerId}
```

预期：返回真实评分、实时完成单和真实完成率。

- [ ] **Step 3: 验证局部覆盖**

后台只填写展示完成单并保存。预期：首页、列表、详情只覆盖完成单；评分和完成率仍为真实值。

- [ ] **Step 4: 验证业务隔离**

打开后台打手详情并查询真实评价/订单。预期：数据未变化；指定打手是否可用仍只由 ACTIVE、在线和未满载决定。

- [ ] **Step 5: 验证清空恢复**

点击“恢复真实值”并保存。预期：三个风采页面恢复真实数据。

- [ ] **Step 6: 验证非法值**

直接请求后台保存接口，分别传评分 `5.01`、完成单 `-1`、完成率 `100.01`。预期：后端返回对应错误，不写入数据库。

---

## Self-Review

- 规格中的可空覆盖、三项范围、真实回退、后台核对、业务隔离和兼容性均有对应任务。
- 公共 DTO 字段名保持不变，小程序不需要新增展示分支。
- 覆盖字段与实体、请求 JSON、数据库列命名一致。
- 无待定项；不包含 Git commit。
