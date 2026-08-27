# 管理后台打手工作状态 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理后台统一展示、筛选打手工作状态，并让订单指派、换人、接力选人使用同一占用口径和禁用规则。

**Architecture:** `delta-common` 提供占用统计 SQL、状态枚举和纯计算器；`delta-player` 提供批量 enrich 与服务端筛选分页；Admin/CS Controller 复用该服务。Web 端以一个状态工具文件统一文案、颜色和可选规则，打手列表每 30 秒刷新，三个选人入口复用同一字段契约。

**Tech Stack:** Java 17、Spring Boot 3.2、MyBatis-Plus、JUnit 5、Mockito、Vue 3、Element Plus、Vite。

## Global Constraints

- 设计依据：`docs/superpowers/specs/2026-08-27-admin-player-work-status-design.md`。
- 不新增数据库字段或 migration。
- 不引入心跳或 WebSocket；在线状态继续使用 `player.is_online`。
- `order.max_active_per_player` 缺失或非法时统一按 `1`。
- 占用状态统一为 `ASSIGNED`、`ACCEPTED`、`WAITING_TEAMMATE`、`IN_PROGRESS`。
- 占用包含 `order.player_id`、`order.player_id2`、`order_player.status = ACCEPTED`，按打手和订单去重。
- 不新增前端测试框架；前端以生产构建和手工联调验证。
- 不执行任何 git 命令，不提交、不推送。

---

## 文件结构

**新增：**

- `delta-game/delta-common/src/main/java/com/delta/common/dto/PlayerActiveOrderStats.java`：批量占用查询结果。
- `delta-game/delta-common/src/main/java/com/delta/common/enums/PlayerWorkStatusEnum.java`：工作状态枚举。
- `delta-game/delta-common/src/main/java/com/delta/common/constant/PlayerOccupancyConstants.java`：配置键、默认值和占用状态。
- `delta-game/delta-common/src/main/java/com/delta/common/service/PlayerWorkStatusCalculator.java`：无数据库依赖的状态优先级计算。
- `delta-game/delta-common/src/main/java/com/delta/common/util/MaxConcurrentConfigParser.java`：安全解析并发上限。
- `delta-game/delta-common/src/test/java/com/delta/common/service/PlayerWorkStatusCalculatorTest.java`：状态优先级与配置解析测试。
- `delta-game/delta-player/src/main/java/com/delta/player/service/PlayerWorkStatusService.java`：批量 enrich 和状态筛选分页接口。
- `delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerWorkStatusServiceImpl.java`：服务实现。
- `delta-game/delta-player/src/test/java/com/delta/player/service/impl/PlayerWorkStatusServiceImplTest.java`：批量 enrich 与分页测试。
- `delta-admin-ui/src/utils/playerWorkStatus.js`：前端状态元数据、占用格式和可选规则。

**修改：**

- `delta-game/delta-common/src/main/java/com/delta/common/mapper/CrossModuleMapper.java`
- `delta-game/delta-common/src/main/java/com/delta/common/mapper/ScheduledTaskMapper.java`
- `delta-game/delta-common/src/main/java/com/delta/common/job/task/OrderAutoAssignTask.java`
- `delta-game/delta-player/src/main/java/com/delta/player/entity/Player.java`
- `delta-game/delta-player/src/main/java/com/delta/player/controller/PlayerOrderController.java`
- `delta-game/delta-player/src/main/java/com/delta/player/controller/UserPlayerController.java`
- `delta-game/delta-order/src/main/java/com/delta/order/service/impl/OrderServiceImpl.java`
- `delta-game/delta-admin/src/main/java/com/delta/admin/controller/AdminPlayerController.java`
- `delta-game/delta-cs/src/main/java/com/delta/cs/controller/CsPlayerController.java`
- `delta-admin-ui/src/views/player/PlayerList.vue`
- `delta-admin-ui/src/views/order/OrderList.vue`
- `delta-admin-ui/src/views/replace/ReplaceList.vue`
- `delta-admin-ui/src/views/relay/RelayList.vue`

---

### Task 1: 公共状态模型与纯计算器

**Files:**
- Create: `delta-game/delta-common/src/main/java/com/delta/common/dto/PlayerActiveOrderStats.java`
- Create: `delta-game/delta-common/src/main/java/com/delta/common/enums/PlayerWorkStatusEnum.java`
- Create: `delta-game/delta-common/src/main/java/com/delta/common/constant/PlayerOccupancyConstants.java`
- Create: `delta-game/delta-common/src/main/java/com/delta/common/service/PlayerWorkStatusCalculator.java`
- Create: `delta-game/delta-common/src/main/java/com/delta/common/util/MaxConcurrentConfigParser.java`
- Test: `delta-game/delta-common/src/test/java/com/delta/common/service/PlayerWorkStatusCalculatorTest.java`

**Interfaces:**
- Produces: `PlayerWorkStatusCalculator.resolve(String, LocalDateTime, Integer, int, int, LocalDateTime): PlayerWorkStatusEnum`
- Produces: `MaxConcurrentConfigParser.parse(String): int`
- Produces: `PlayerActiveOrderStats#getPlayerId/getActiveOrders/getPendingAssignedOrders`

- [ ] **Step 1: 先写状态优先级失败测试**

```java
package com.delta.common.service;

import com.delta.common.enums.PlayerWorkStatusEnum;
import com.delta.common.util.MaxConcurrentConfigParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerWorkStatusCalculatorTest {
    private final PlayerWorkStatusCalculator calculator = new PlayerWorkStatusCalculator();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 27, 14, 0);

    @Test
    void shouldResolveStatusesByPriority() {
        assertEquals(PlayerWorkStatusEnum.ACCOUNT_ABNORMAL,
                calculator.resolve("FROZEN", null, 1, 0, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.ACCOUNT_ABNORMAL,
                calculator.resolve("ACTIVE", now.plusHours(1), 1, 0, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.OFFLINE,
                calculator.resolve("ACTIVE", null, 0, 0, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.ASSIGNED_PENDING,
                calculator.resolve("ACTIVE", null, 1, 1, 1, 1, now));
        assertEquals(PlayerWorkStatusEnum.FULL,
                calculator.resolve("ACTIVE", null, 1, 1, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.IN_SERVICE,
                calculator.resolve("ACTIVE", null, 1, 1, 0, 2, now));
        assertEquals(PlayerWorkStatusEnum.AVAILABLE,
                calculator.resolve("ACTIVE", null, 1, 0, 0, 1, now));
    }

    @Test
    void shouldParseMaxConcurrentSafely() {
        assertEquals(5, MaxConcurrentConfigParser.parse("5"));
        assertEquals(1, MaxConcurrentConfigParser.parse(null));
        assertEquals(1, MaxConcurrentConfigParser.parse(""));
        assertEquals(1, MaxConcurrentConfigParser.parse("abc"));
        assertEquals(1, MaxConcurrentConfigParser.parse("0"));
        assertEquals(1, MaxConcurrentConfigParser.parse("-1"));
    }
}
```

- [ ] **Step 2: 运行测试，确认因类不存在而失败**

Run:

```bash
cd delta-game
mvn -pl delta-common test -Dtest=PlayerWorkStatusCalculatorTest
```

Expected: FAIL，提示 `PlayerWorkStatusCalculator`、`PlayerWorkStatusEnum` 或 `MaxConcurrentConfigParser` 不存在。

- [ ] **Step 3: 实现枚举、常量、DTO、解析器和计算器**

```java
public enum PlayerWorkStatusEnum {
    ACCOUNT_ABNORMAL, OFFLINE, ASSIGNED_PENDING, FULL, IN_SERVICE, AVAILABLE
}
```

```java
public final class PlayerOccupancyConstants {
    public static final String MAX_ACTIVE_CONFIG_KEY = "order.max_active_per_player";
    public static final String DEFAULT_MAX_ACTIVE = "1";
    public static final List<String> ACTIVE_ORDER_STATUSES =
            List.of("ASSIGNED", "ACCEPTED", "WAITING_TEAMMATE", "IN_PROGRESS");

    private PlayerOccupancyConstants() {}
}
```

```java
@Data
public class PlayerActiveOrderStats {
    private Long playerId;
    private Integer activeOrders;
    private Integer pendingAssignedOrders;
}
```

```java
public final class MaxConcurrentConfigParser {
    private MaxConcurrentConfigParser() {}

    public static int parse(String rawValue) {
        try {
            int parsed = Integer.parseInt(rawValue);
            return parsed > 0 ? parsed : 1;
        } catch (NumberFormatException | NullPointerException ignored) {
            return 1;
        }
    }
}
```

```java
@Component
public class PlayerWorkStatusCalculator {
    public PlayerWorkStatusEnum resolve(
            String accountStatus,
            LocalDateTime frozenUntil,
            Integer isOnline,
            int activeOrders,
            int pendingAssignedOrders,
            int maxConcurrent,
            LocalDateTime now) {
        if (!"ACTIVE".equals(accountStatus)
                || (frozenUntil != null && frozenUntil.isAfter(now))) {
            return PlayerWorkStatusEnum.ACCOUNT_ABNORMAL;
        }
        if (!Integer.valueOf(1).equals(isOnline)) {
            return PlayerWorkStatusEnum.OFFLINE;
        }
        if (pendingAssignedOrders > 0) {
            return PlayerWorkStatusEnum.ASSIGNED_PENDING;
        }
        if (activeOrders >= maxConcurrent) {
            return PlayerWorkStatusEnum.FULL;
        }
        if (activeOrders > 0) {
            return PlayerWorkStatusEnum.IN_SERVICE;
        }
        return PlayerWorkStatusEnum.AVAILABLE;
    }
}
```

- [ ] **Step 4: 运行公共模块测试**

Run:

```bash
mvn -pl delta-common test -Dtest=PlayerWorkStatusCalculatorTest
```

Expected: PASS，2 个测试方法全部通过。

---

### Task 2: 统一占用统计 SQL

**Files:**
- Modify: `delta-game/delta-common/src/main/java/com/delta/common/mapper/CrossModuleMapper.java`
- Modify: `delta-game/delta-common/src/main/java/com/delta/common/mapper/ScheduledTaskMapper.java`

**Interfaces:**
- Consumes: `PlayerActiveOrderStats`
- Produces: `selectPlayerActiveOrders(Long): int`
- Produces: `batchSelectPlayerActiveOrderStats(List<Long>): List<PlayerActiveOrderStats>`
- Produces: `ScheduledTaskMapper.selectAvailablePlayers(int)` 使用相同占用语义

- [ ] **Step 1: 替换单人占用查询**

在 `CrossModuleMapper` 中用 `UNION` 聚合三种归属，避免 `order_player` 与 `player_id2` 重复计数：

```java
@Select("""
    SELECT COUNT(DISTINCT occupied.order_id)
    FROM (
        SELECT o.id AS order_id
        FROM `order` o
        WHERE o.player_id = #{playerId}
          AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
        UNION
        SELECT o.id AS order_id
        FROM `order` o
        WHERE o.player_id2 = #{playerId}
          AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
        UNION
        SELECT op.order_id
        FROM order_player op
        INNER JOIN `order` o ON o.id = op.order_id
        WHERE op.player_id = #{playerId}
          AND op.status = 'ACCEPTED'
          AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
    ) occupied
    """)
int selectPlayerActiveOrders(@Param("playerId") Long playerId);
```

- [ ] **Step 2: 新增批量占用查询**

```java
@Select("""
    <script>
    SELECT t.player_id AS playerId,
           COUNT(DISTINCT t.order_id) AS activeOrders,
           COUNT(DISTINCT CASE WHEN t.order_status = 'ASSIGNED' THEN t.order_id END)
               AS pendingAssignedOrders
    FROM (
        SELECT o.player_id AS player_id, o.id AS order_id, o.status AS order_status
        FROM `order` o
        WHERE o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
          AND o.player_id IN
          <foreach collection="playerIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        UNION
        SELECT o.player_id2, o.id, o.status
        FROM `order` o
        WHERE o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
          AND o.player_id2 IN
          <foreach collection="playerIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        UNION
        SELECT op.player_id, op.order_id, o.status
        FROM order_player op
        INNER JOIN `order` o ON o.id = op.order_id
        WHERE op.status = 'ACCEPTED'
          AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
          AND op.player_id IN
          <foreach collection="playerIds" item="id" open="(" separator="," close=")">#{id}</foreach>
    ) t
    GROUP BY t.player_id
    </script>
    """)
List<PlayerActiveOrderStats> batchSelectPlayerActiveOrderStats(
        @Param("playerIds") List<Long> playerIds);
```

调用方必须在 `playerIds` 为空时直接返回，禁止执行空 `IN ()`。

- [ ] **Step 3: 改造自动派单可用打手 SQL**

将 `ScheduledTaskMapper.selectAvailablePlayers` 中仅统计 `o.player_id` 的相关子查询，改为先对全量三来源按 `player_id + order_id` 聚合，再 `LEFT JOIN` 到 `player`。不要在 derived table 内引用外层 `p.id`，避免 MySQL 对相关派生表的版本兼容问题：

```sql
SELECT p.id
FROM player p
LEFT JOIN (
    SELECT occupied.player_id, COUNT(DISTINCT occupied.order_id) AS active_orders
    FROM (
        SELECT o.player_id, o.id AS order_id
        FROM `order` o
        WHERE o.player_id IS NOT NULL
          AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
        UNION
        SELECT o.player_id2, o.id
        FROM `order` o
        WHERE o.player_id2 IS NOT NULL
          AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
        UNION
        SELECT op.player_id, op.order_id
        FROM order_player op
        INNER JOIN `order` o ON o.id = op.order_id
        WHERE op.status = 'ACCEPTED'
          AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
    ) occupied
    GROUP BY occupied.player_id
) occupancy ON occupancy.player_id = p.id
```

随后保留：

```sql
p.status = 'ACTIVE'
AND p.deleted = 0
AND p.is_online = 1
AND (p.frozen_until IS NULL OR p.frozen_until < NOW())
AND COALESCE(occupancy.active_orders, 0) < #{maxActive}
```

三来源必须包含 `ASSIGNED`，`order_player` 仅包含 `ACCEPTED`。

- [ ] **Step 4: 编译 Mapper 注解 SQL**

Run:

```bash
cd delta-game
mvn -pl delta-common compile
mvn -pl delta-admin compile -am
```

Expected: BUILD SUCCESS；无 MyBatis 注解字符串、DTO 映射或模块依赖编译错误。

---

### Task 3: 打手工作状态服务与服务端筛选分页

**Files:**
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/entity/Player.java`
- Create: `delta-game/delta-player/src/main/java/com/delta/player/service/PlayerWorkStatusService.java`
- Create: `delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerWorkStatusServiceImpl.java`
- Test: `delta-game/delta-player/src/test/java/com/delta/player/service/impl/PlayerWorkStatusServiceImplTest.java`

**Interfaces:**
- Produces: `void enrichOne(Player player)`
- Produces: `void enrichBatch(List<Player> players)`
- Produces: `Page<Player> queryPage(PageQuery query, LambdaQueryWrapper<Player> wrapper, String workStatus)`
- Produces: `int getMaxConcurrent()`

- [ ] **Step 1: 给 `Player` 增加瞬态字段**

```java
@TableField(exist = false)
private String workStatus;
@TableField(exist = false)
private Integer pendingAssignedOrders;
@TableField(exist = false)
private Integer maxConcurrent;
```

- [ ] **Step 2: 先写批量 enrich 与筛选分页测试**

测试至少覆盖：

```java
@Test
void enrichBatchShouldFillZeroStatsAndResolveStatus() {
    Player available = player(1L, "ACTIVE", 1);
    Player full = player(2L, "ACTIVE", 1);
    PlayerActiveOrderStats fullStats = stats(2L, 1, 0);

    when(sysConfigService.getConfigValue("order.max_active_per_player", "1"))
            .thenReturn("1");
    when(crossModuleMapper.batchSelectPlayerActiveOrderStats(List.of(1L, 2L)))
            .thenReturn(List.of(fullStats));

    service.enrichBatch(List.of(available, full));

    assertEquals("AVAILABLE", available.getWorkStatus());
    assertEquals(0, available.getActiveOrders());
    assertEquals("FULL", full.getWorkStatus());
    assertEquals(1, full.getActiveOrders());
}

@Test
void queryPageShouldFilterBeforeSlicing() {
    // 准备 3 个候选：AVAILABLE、OFFLINE、AVAILABLE；pageSize=1。
    // 断言 total=2，第二页记录仍为 AVAILABLE。
}
```

使用 `@ExtendWith(MockitoExtension.class)` Mock：

- `PlayerService`
- `CrossModuleMapper`
- `SysConfigService`
- `PlayerWorkStatusCalculator`

- [ ] **Step 3: 运行测试，确认失败**

Run:

```bash
cd delta-game
mvn -pl delta-player test -Dtest=PlayerWorkStatusServiceImplTest
```

Expected: FAIL，提示 Service 或新增字段尚未实现。

- [ ] **Step 4: 实现服务**

接口：

```java
public interface PlayerWorkStatusService {
    void enrichOne(Player player);
    void enrichBatch(List<Player> players);
    Page<Player> queryPage(
            PageQuery query,
            LambdaQueryWrapper<Player> wrapper,
            String workStatus);
    int getMaxConcurrent();
}
```

实现约束：

1. `enrichBatch` 空列表直接返回。
2. 一次提取所有 player ID，一次调用批量 Mapper。
3. 无统计记录补 `activeOrders=0`、`pendingAssignedOrders=0`。
4. 每行写入相同 `maxConcurrent`。
5. 使用 `LocalDateTime.now()` 的同一个快照计算整批状态。
6. `workStatus` 为空时走数据库分页后 enrich。
7. `workStatus` 非空时先查候选、批量 enrich、过滤，再内存切页。
8. `pageNum < 1`、`pageSize < 1` 依照 `PageQuery` 现有默认行为，不引入新规则。

核心过滤切页：

```java
List<Player> filtered = candidates.stream()
        .filter(player -> workStatus.equals(player.getWorkStatus()))
        .toList();
long total = filtered.size();
int from = Math.min((query.getPageNum() - 1) * query.getPageSize(), filtered.size());
int to = Math.min(from + query.getPageSize(), filtered.size());
return new Page<Player>(query.getPageNum(), query.getPageSize(), total)
        .setRecords(filtered.subList(from, to));
```

- [ ] **Step 5: 运行服务测试**

Run:

```bash
mvn -pl delta-player test -Dtest=PlayerWorkStatusServiceImplTest
```

Expected: PASS；批量缺省值、优先级、过滤后 total、第二页切片均正确。

---

### Task 4: 接入 Admin/CS 列表及详情

**Files:**
- Modify: `delta-game/delta-admin/src/main/java/com/delta/admin/controller/AdminPlayerController.java`
- Modify: `delta-game/delta-cs/src/main/java/com/delta/cs/controller/CsPlayerController.java`

**Interfaces:**
- Consumes: `PlayerWorkStatusService`
- Produces: `/admin/player/list` 与 `/cs/player/list` 接受 `workStatus`
- Produces: `/cs/player/assign-list` 行内完整状态字段，顶层继续返回 `maxConcurrent`

- [ ] **Step 1: 改 Admin 列表和详情**

注入 `PlayerWorkStatusService`。`list` 新增：

```java
@RequestParam(value = "workStatus", required = false) String workStatus
```

保留 keyword、账号 status wrapper；分页改为：

```java
Page<Player> page = playerWorkStatusService.queryPage(query, wrapper, workStatus);
```

完成订单数和余额只对当前页 records enrich，避免给所有候选做钱包 N+1 查询。详情在原有余额/完成数后调用：

```java
playerWorkStatusService.enrichOne(player);
```

- [ ] **Step 2: 改 CS 列表、详情和 assign-list**

- `list` 与 Admin 相同，删除逐行 `selectPlayerActiveOrders`；保留当前页逐行填充 `completedOrders` 的行为。
- `detail` 改为 `enrichOne`。
- `assign-list` 仍限定 `status=ACTIVE`，数据库分页后调用 `enrichBatch(page.getRecords())`。
- 顶层 `maxConcurrent` 使用 `playerWorkStatusService.getMaxConcurrent()`。

- [ ] **Step 3: 编译所有调用模块**

Run:

```bash
cd delta-game
mvn -pl delta-admin,delta-cs -am compile
```

Expected: BUILD SUCCESS；Controller 构造注入、泛型和返回结构不报错。

---

### Task 5: 统一指派、自助接单和自动派单上限

**Files:**
- Modify: `delta-game/delta-order/src/main/java/com/delta/order/service/impl/OrderServiceImpl.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/controller/PlayerOrderController.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/controller/UserPlayerController.java`
- Modify: `delta-game/delta-common/src/main/java/com/delta/common/job/task/OrderAutoAssignTask.java`

**Interfaces:**
- Consumes: `CrossModuleMapper.selectPlayerActiveOrders`
- Consumes: `MaxConcurrentConfigParser`
- Consumes: `PlayerWorkStatusService.enrichBatch`

- [ ] **Step 1: 统一 `OrderServiceImpl.assignOrder`**

读取配置统一为：

```java
int maxConcurrent = MaxConcurrentConfigParser.parse(
        sysConfigService.getConfigValue(
                PlayerOccupancyConstants.MAX_ACTIVE_CONFIG_KEY,
                PlayerOccupancyConstants.DEFAULT_MAX_ACTIVE));
```

主/辅助打手都使用新 `selectPlayerActiveOrders`。保留现有异常文案和同一打手不能重复选择校验。

- [ ] **Step 2: 统一打手自助接单**

在 `PlayerOrderController.accept` 删除直接对 `OrderService` 的旧状态 count，改为：

```java
int activeOrders = crossModuleMapper.selectPlayerActiveOrders(playerId);
if (activeOrders >= maxActive) {
    throw new BusinessException(
            "当前已有" + activeOrders + "个进行中订单，已达最大接单数" + maxActive);
}
```

`maxActive` 通过同一解析器取得。由于待接受的当前 `ASSIGNED` 单已计入占用，必须确认业务顺序：若该订单正是当前打手自己的 `ASSIGNED` 单，接受前上限判断应排除当前订单，避免 `max=1` 时无法接受已指派订单。实现以下 Mapper 重载或专用方法：

```java
int selectPlayerActiveOrdersExcludingOrder(
        @Param("playerId") Long playerId,
        @Param("excludedOrderId") Long excludedOrderId);
```

`accept` 使用排除当前订单后的占用数；新接大厅订单没有预占时结果不受影响。

- [ ] **Step 3: 统一候选打手接口**

- `PlayerOrderController.teammateCandidates` 使用 `PlayerWorkStatusService.enrichBatch`。
- `UserPlayerController.availablePlayers` 使用 `PlayerWorkStatusService.enrichBatch`。
- 顶层 `maxConcurrent` 使用 `getMaxConcurrent()`。
- 删除这些位置的 `"5"` 缺省值和逐人 activeOrders 查询。

- [ ] **Step 4: 统一自动派单配置解析**

`OrderAutoAssignTask` 使用默认 `1`，非法值也回退到 `1`：

```java
int maxActive = MaxConcurrentConfigParser.parse(
        scheduledTaskMapper.selectConfigValue(
                PlayerOccupancyConstants.MAX_ACTIVE_CONFIG_KEY));
```

- [ ] **Step 5: 后端回归**

Run:

```bash
cd delta-game
mvn -pl delta-common,delta-order,delta-player test
mvn -pl delta-admin,delta-cs -am compile
```

Expected: BUILD SUCCESS；已有测试和新增测试全部通过。

---

### Task 6: 前端状态工具与打手管理列表

**Files:**
- Create: `delta-admin-ui/src/utils/playerWorkStatus.js`
- Modify: `delta-admin-ui/src/views/player/PlayerList.vue`

**Interfaces:**
- Produces: `workStatusMeta(status)`
- Produces: `formatActiveOrders(player, fallbackMax)`
- Produces: `isPlayerSelectable(player)`
- Produces: `isPlayerRowDimmed(player)`
- Produces: `guardPlayerSelection(player): boolean`

- [ ] **Step 1: 新建前端状态工具**

```javascript
import { ElMessage } from 'element-plus'

export const WORK_STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '可接单', value: 'AVAILABLE' },
  { label: '待接单确认', value: 'ASSIGNED_PENDING' },
  { label: '服务中', value: 'IN_SERVICE' },
  { label: '已满单', value: 'FULL' },
  { label: '离线', value: 'OFFLINE' },
  { label: '账号异常', value: 'ACCOUNT_ABNORMAL' },
]

const META = {
  AVAILABLE: { label: '可接单', type: 'success' },
  ASSIGNED_PENDING: { label: '待接单确认', type: 'warning' },
  IN_SERVICE: { label: '服务中', type: 'warning' },
  FULL: { label: '已满单', type: 'danger' },
  OFFLINE: { label: '离线', type: 'info' },
  ACCOUNT_ABNORMAL: { label: '账号异常', type: 'danger' },
}

export function workStatusMeta(status) {
  return META[status] || { label: status || '-', type: 'info' }
}

export function formatActiveOrders(player, fallbackMax = 1) {
  const active = Number(player.activeOrders || 0)
  const max = Number(player.maxConcurrent || fallbackMax || 1)
  const pending = Number(player.pendingAssignedOrders || 0)
  return `进行中 ${active}/${max}${pending > 0 ? ` · 待确认 ${pending}` : ''}`
}

export function isPlayerSelectable(player) {
  const active = Number(player.activeOrders || 0)
  const max = Number(player.maxConcurrent || 1)
  return player.isOnline === 1
    && active < max
    && !['OFFLINE', 'FULL', 'ACCOUNT_ABNORMAL'].includes(player.workStatus)
}

export function isPlayerRowDimmed(player) {
  return player.isOnline !== 1
    || ['OFFLINE', 'ACCOUNT_ABNORMAL'].includes(player.workStatus)
}

export function guardPlayerSelection(player) {
  if (isPlayerSelectable(player)) return true
  const active = Number(player.activeOrders || 0)
  const max = Number(player.maxConcurrent || 1)
  if (player.workStatus === 'FULL' || active >= max) ElMessage.warning('已达最大接单数')
  else if (player.workStatus === 'ACCOUNT_ABNORMAL') ElMessage.warning('打手账号异常，无法指派')
  else ElMessage.warning('打手当前离线，无法指派')
  return false
}
```

- [ ] **Step 2: 增加列表筛选和列**

`query` 改为：

```javascript
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: '',
  workStatus: 'AVAILABLE',
})
```

新增工作状态下拉；重置后 `workStatus='AVAILABLE'`。在账号状态列前增加：

- 工作状态 Tag，使用 `workStatusMeta`。
- 占用文本，使用 `formatActiveOrders`。
- 在线 Tag 和 `lastOnlineAt`。

`el-table` 增加行样式：

```javascript
function playerRowClass({ row }) {
  return isPlayerRowDimmed(row) ? 'dimmed-row' : ''
}
```

- [ ] **Step 3: 实现 30 秒静默轮询**

```javascript
const POLL_INTERVAL_MS = 30_000
const lastUpdatedAt = ref(null)
let pollTimer = null

async function fetchData({ silent = false } = {}) {
  if (!silent) loading.value = true
  try {
    const fn = isAdmin ? adminPlayerList : csPlayerList
    const res = await fn(query)
    list.value = res.data.records ?? []
    total.value = Number(res.data.total ?? 0)
    lastUpdatedAt.value = new Date()
  } catch {
    // request 拦截器已提示；保留旧列表和上次成功更新时间。
  } finally {
    if (!silent) loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => fetchData({ silent: true }), POLL_INTERVAL_MS)
}

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}

onMounted(() => {
  fetchData()
  startPolling()
})
onBeforeUnmount(stopPolling)
```

模板增加手动刷新按钮和 `HH:mm 更新`；只在成功请求后更新时间。

- [ ] **Step 4: 构建前端**

Run:

```bash
cd delta-admin-ui
npm run build
```

Expected: Vite build 成功，无未导入的 Vue lifecycle 或工具函数。

---

### Task 7: 三处选打手入口统一展示与禁用

**Files:**
- Modify: `delta-admin-ui/src/views/order/OrderList.vue`
- Modify: `delta-admin-ui/src/views/replace/ReplaceList.vue`
- Modify: `delta-admin-ui/src/views/relay/RelayList.vue`

**Interfaces:**
- Consumes: `workStatusMeta`
- Consumes: `formatActiveOrders`
- Consumes: `isPlayerSelectable`
- Consumes: `isPlayerRowDimmed`
- Consumes: `guardPlayerSelection`

- [ ] **Step 1: 改订单指派主/辅助打手表格**

两张表统一新增：

```vue
<el-table-column label="工作状态" width="120">
  <template #default="{ row }">
    <el-tag :type="workStatusMeta(row.workStatus).type" size="small">
      {{ workStatusMeta(row.workStatus).label }}
    </el-tag>
  </template>
</el-table-column>
<el-table-column label="占用" min-width="150">
  <template #default="{ row }">{{ formatActiveOrders(row, maxConcurrent) }}</template>
</el-table-column>
```

选择按钮：

```vue
:disabled="!isPlayerSelectable(row)"
```

选择函数首行防御：

```javascript
if (!guardPlayerSelection(row)) return
```

行样式改为 `isPlayerRowDimmed(row) ? 'dimmed-row' : ''`。拉取 assign-list 后记录顶层 `maxConcurrent`。

- [ ] **Step 2: 改换人选打手表格**

- 将裸 `activeOrders` 改为 `formatActiveOrders(row, maxConcurrent)`。
- 增加工作状态和在线列。
- 增加 `row-class-name`。
- 选择按钮禁用并在 `doApproveAssign` 前调用 guard。
- 请求失败不清空旧列表；删除与 request 拦截器重复的错误提示。

- [ ] **Step 3: 改接力选打手表格**

禁用规则需同时保留“不能选本人”：

```vue
:disabled="row.id === approveRow?.originalPlayerId || !isPlayerSelectable(row)"
```

按钮文案优先显示“本人”；其他不可选打手仍显示“选择”但 disabled。`confirmApprove` 在原打手判断后调用 guard。

- [ ] **Step 4: 统一灰显样式**

三个文件统一：

```css
:deep(.dimmed-row) {
  opacity: 0.5;
}
```

删除 `OrderList.vue` 的旧 `.offline-row`。

- [ ] **Step 5: 前端生产构建**

Run:

```bash
cd delta-admin-ui
npm run build
```

Expected: BUILD SUCCESS；三个页面不存在未定义变量、错误 import 或模板表达式错误。

---

### Task 8: 全量验证与手工验收

**Files:**
- Verify only; no new files required.

**Interfaces:**
- Verifies all API and UI contracts from Tasks 1–7.

- [ ] **Step 1: 运行后端全量测试**

Run:

```bash
cd delta-game
mvn test
```

Expected: BUILD SUCCESS，所有模块测试通过。

- [ ] **Step 2: 构建 Admin 后端与 Web**

Run:

```bash
cd delta-game
mvn -pl delta-admin package -DskipTests
cd ../delta-admin-ui
npm run build
```

Expected: 两条命令均成功。

- [ ] **Step 3: API 验收**

依次检查：

1. `/admin/player/list?workStatus=AVAILABLE&pageNum=1&pageSize=10`：所有记录均为 `AVAILABLE`，total 是过滤后数量。
2. 请求第二页：没有 `OFFLINE` 等其他状态混入。
3. `/admin/player/{id}` 与 `/cs/player/{id}`：同一打手 `activeOrders`、`pendingAssignedOrders`、`workStatus` 一致。
4. `/cs/player/assign-list`：每行有工作状态字段，顶层有 `maxConcurrent`。
5. 仅作为 `player_id2` 或 ACCEPTED 队友的打手：占用 +1。
6. 同一订单同时存在 `player_id2` 和 `order_player`：只计 1。
7. 已有 ASSIGNED 的打手：显示 `ASSIGNED_PENDING`，但接受当前指派单不会被自身预占错误阻断。
8. `ASSIGNED_PENDING` 达到上限时虽然保留待确认文案，选人按钮仍禁用。
9. 满单打手：手动指派、自动派单、自助接大厅新单都不能突破上限。

- [ ] **Step 4: Web 手工验收**

1. Admin 和 CS 打开打手管理，默认筛选“可接单”。
2. 切换六种工作状态，分页 total 正确。
3. 表格显示工作状态、进行中 `x/y`、待确认数、在线和最后在线时间。
4. 30 秒后数据静默刷新，不出现全表闪烁。
5. 模拟请求失败：旧列表保留，成功更新时间不变化。
6. 订单指派主/辅助、换人、接力三处显示相同状态和占用。
7. 离线、已满单、账号异常不可选；服务中未满、待接单确认未满仍可选。
8. 接力场景原打手始终不可选。

- [ ] **Step 5: 最终范围检查**

- 无数据库 migration。
- 无心跳/WebSocket 改动。
- 无小程序 UI 改动。
- 无新增前端依赖。
- 无任何 git 操作。
