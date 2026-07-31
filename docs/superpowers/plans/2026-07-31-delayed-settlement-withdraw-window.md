# 打手延迟入账与提现时间窗口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 订单确认后打手收益延迟 N 天入账并展示待入账；提现仅在可配置时间窗口内可申请。

**Architecture:** 确认时写入 `pending_balance`（`settled=2` + `settle_available_at`），小时任务转入 `balance`；投诉扣款先扣订单剩余 `order_player.settle_amount`/pending 再扣 balance。提现窗口由 `sys_config` JSON 驱动，纯函数校验。合并现有双结算监听，以 `SettlementEventListener` 为唯一入账入口。

**Tech Stack:** Spring Boot / MyBatis-Plus / Flyway / `@Scheduled` / uni-app 打手端 / JUnit 5

**Spec:** `docs/superpowers/specs/2026-07-31-delayed-settlement-withdraw-window-design.md`

## Global Constraints

- `settled`：`0` 未结算 / `2` 待入账 / `1` 已入账；历史 `settled=1` 不回溯。
- 延迟天数 `settlement.delay_days` 默认 `5`；只影响新确认订单（靠 `settle_available_at` 固化）。
- 提现窗口默认：周二 12:00≤t＜周三 12:00、周六 12:00≤t＜周日 12:00（左闭右开）。
- 确认时不写 `INCOME`、不增 `totalIncome`；真正入账时再写。
- pending 与 `settleAmount` 不一致：整单跳过入账 + error 日志。
- 首版不做待入账明细列表、不做可视化排班 UI。
- **必须消除双监听重复结算**：`PlayerIncomeServiceImpl.onOrderConfirmed` 与 `SettlementEventListener` 目前都会监听 `OrderConfirmedEvent`。

---

## File Structure

| 路径 | 职责 |
|------|------|
| `delta-game/delta-admin/src/main/resources/db/migration/V20260731__delayed_settlement_withdraw_window.sql` | 表字段、索引、sys_config |
| `docs/migrations/2026-07-31-delayed-settlement-withdraw-window.sql` | 运维侧同步脚本（与 Flyway 同内容） |
| `delta-player/.../entity/PlayerWallet.java` | `pendingBalance` |
| `delta-order/.../entity/Order.java` | `settleAvailableAt` |
| `delta-player/.../service/impl/PlayerWalletServiceImpl.java` | `initWallet` 初始化 pending |
| `delta-player/.../service/PlayerIncomeService.java` + Impl | 入账释放、退款扣减；**去掉** `@EventListener` |
| `delta-admin/.../listener/SettlementEventListener.java` | 确认 → 记 pending（唯一监听入口） |
| `delta-admin/.../job/PendingSettlementTask.java`（新） | 每小时调用释放入账 |
| `delta-player/.../util/WithdrawTimeWindowHelper.java`（新） | 窗口解析与判定（纯函数） |
| `delta-player/.../controller/WithdrawController.java` | 窗口校验 + `GET /window` |
| `delta-player/.../controller/PlayerEarningsController.java` | summary 增加字段 |
| `delta-cs/.../CsComplaintController.java` | `refundPlayerIncomeIfSettled` 支持 settled=2 |
| `delta-player/pom.xml` | 增加 `spring-boot-starter-test` |
| `delta-player/src/test/.../WithdrawTimeWindowHelperTest.java` | 窗口单测 |
| `delta-mp/api/player.js` | `getWithdrawWindow` |
| `delta-mp/constants/withdrawRules.js` | 文案对齐默认窗口 |
| `delta-mp/pages-player/earnings/index.vue` | 待入账展示 |
| `delta-mp/pages-player/withdraw/index.vue` | 待入账 + 窗口态 |

---

### Task 1: DB 迁移 + 实体字段

**Files:**
- Create: `delta-game/delta-admin/src/main/resources/db/migration/V20260731__delayed_settlement_withdraw_window.sql`
- Create: `docs/migrations/2026-07-31-delayed-settlement-withdraw-window.sql`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/entity/PlayerWallet.java`
- Modify: `delta-game/delta-order/src/main/java/com/delta/order/entity/Order.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerWalletServiceImpl.java`

**Interfaces:**
- Produces: `PlayerWallet.pendingBalance: BigDecimal`；`Order.settleAvailableAt: LocalDateTime`；config keys `settlement.delay_days`、`withdraw.time_windows`

- [ ] **Step 1: 写 Flyway SQL**

```sql
ALTER TABLE `player_wallet`
  ADD COLUMN `pending_balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '待入账金额' AFTER `balance`;

ALTER TABLE `order`
  ADD COLUMN `settle_available_at` DATETIME NULL COMMENT '预计入账时间（确认+延迟天数）' AFTER `settle_time`;

-- settled: 0未结算 1已入账 2待入账
CREATE INDEX `idx_order_pending_settle` ON `order` (`settled`, `settle_available_at`);

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'settlement.delay_days', '结算延迟入账天数', '5', 'number', '结算配置', '用户确认后满N天转入可提现', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'settlement.delay_days');

INSERT INTO `sys_config` (`config_key`, `config_name`, `config_value`, `value_type`, `config_group`, `remark`, `created_at`, `updated_at`)
SELECT 'withdraw.time_windows', '提现时间窗口',
       '[{"startDow":2,"startTime":"12:00","endDow":3,"endTime":"12:00"},{"startDow":6,"startTime":"12:00","endDow":7,"endTime":"12:00"}]',
       'text', '提现配置', 'JSON数组，dow:1=周一..7=周日，区间左闭右开', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `config_key` = 'withdraw.time_windows');
```

将相同内容写入 `docs/migrations/2026-07-31-delayed-settlement-withdraw-window.sql`。

- [ ] **Step 2: 实体加字段**

`PlayerWallet` 增加：

```java
private BigDecimal pendingBalance;
```

`Order` 在 `settleTime` 后增加：

```java
/** 预计入账时间；settled=2 时有效 */
private LocalDateTime settleAvailableAt;
```

- [ ] **Step 3: `initWallet` 初始化 pending**

```java
wallet.setPendingBalance(BigDecimal.ZERO);
```

- [ ] **Step 4: Commit**

```bash
git add delta-game/delta-admin/src/main/resources/db/migration/V20260731__delayed_settlement_withdraw_window.sql \
  docs/migrations/2026-07-31-delayed-settlement-withdraw-window.sql \
  delta-game/delta-player/src/main/java/com/delta/player/entity/PlayerWallet.java \
  delta-game/delta-order/src/main/java/com/delta/order/entity/Order.java \
  delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerWalletServiceImpl.java
git commit -m "$(cat <<'EOF'
feat(db): 待入账余额与提现窗口配置字段

为延迟结算与可配置提现窗口增加 pending_balance、settle_available_at 和 sys_config。
EOF
)"
```

---

### Task 2: 提现窗口纯函数 + 单测

**Files:**
- Modify: `delta-game/delta-player/pom.xml`
- Create: `delta-game/delta-player/src/main/java/com/delta/player/util/WithdrawTimeWindowHelper.java`
- Create: `delta-game/delta-player/src/test/java/com/delta/player/util/WithdrawTimeWindowHelperTest.java`

**Interfaces:**
- Produces:
  - `List<Window> defaultWindows()`
  - `List<Window> parseWindows(String json)` — 非法则默认双窗口
  - `boolean isInWindow(LocalDateTime now, List<Window> windows)` — 左闭右开
  - `String buildWindowsText(List<Window> windows)`
  - `record Window(int startDow, LocalTime startTime, int endDow, LocalTime endTime)`

- [ ] **Step 1: pom 加测试依赖**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 写单测**

```java
package com.delta.player.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class WithdrawTimeWindowHelperTest {
    @Test
    void tuesdayNoon_inWindow() {
        var windows = WithdrawTimeWindowHelper.defaultWindows();
        // 2026-07-28 为周二
        assertTrue(WithdrawTimeWindowHelper.isInWindow(
                LocalDateTime.of(2026, 7, 28, 12, 0), windows));
    }

    @Test
    void wednesdayNoon_outOfWindow_halfOpen() {
        var windows = WithdrawTimeWindowHelper.defaultWindows();
        assertFalse(WithdrawTimeWindowHelper.isInWindow(
                LocalDateTime.of(2026, 7, 29, 12, 0), windows));
    }

    @Test
    void monday_outOfWindow() {
        var windows = WithdrawTimeWindowHelper.defaultWindows();
        assertFalse(WithdrawTimeWindowHelper.isInWindow(
                LocalDateTime.of(2026, 7, 27, 15, 0), windows));
    }

    @Test
    void invalidJson_fallsBackToDefault() {
        assertEquals(2, WithdrawTimeWindowHelper.parseWindows("not-json").size());
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
cd delta-game && mvn -pl delta-player -am test -Dtest=WithdrawTimeWindowHelperTest
```

Expected: 编译失败（类不存在）

- [ ] **Step 4: 实现 Helper**

```java
package com.delta.player.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public final class WithdrawTimeWindowHelper {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] DOW_CN = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private WithdrawTimeWindowHelper() {}

    public record Window(int startDow, LocalTime startTime, int endDow, LocalTime endTime) {}

    public static List<Window> defaultWindows() {
        return List.of(
                new Window(2, LocalTime.of(12, 0), 3, LocalTime.of(12, 0)),
                new Window(6, LocalTime.of(12, 0), 7, LocalTime.of(12, 0))
        );
    }

    public static List<Window> parseWindows(String json) {
        if (json == null || json.isBlank()) return defaultWindows();
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json, new TypeReference<>() {});
            List<Window> list = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                int startDow = ((Number) m.get("startDow")).intValue();
                int endDow = ((Number) m.get("endDow")).intValue();
                LocalTime startTime = LocalTime.parse(String.valueOf(m.get("startTime")));
                LocalTime endTime = LocalTime.parse(String.valueOf(m.get("endTime")));
                list.add(new Window(startDow, startTime, endDow, endTime));
            }
            if (list.isEmpty()) return defaultWindows();
            return list;
        } catch (Exception e) {
            log.warn("withdraw.time_windows 解析失败，使用默认窗口: {}", e.getMessage());
            return defaultWindows();
        }
    }

    public static boolean isInWindow(LocalDateTime now, List<Window> windows) {
        if (windows == null || windows.isEmpty()) return false;
        int dow = now.getDayOfWeek().getValue();
        LocalTime t = now.toLocalTime();
        long nowMin = (dow - 1L) * 24 * 60 + t.getHour() * 60L + t.getMinute();
        for (Window w : windows) {
            long startMin = (w.startDow() - 1L) * 24 * 60
                    + w.startTime().getHour() * 60L + w.startTime().getMinute();
            long endMin = (w.endDow() - 1L) * 24 * 60
                    + w.endTime().getHour() * 60L + w.endTime().getMinute();
            if (endMin <= startMin) {
                long weekEnd = 7L * 24 * 60;
                if ((nowMin >= startMin && nowMin < weekEnd) || (nowMin >= 0 && nowMin < endMin)) {
                    return true;
                }
            } else if (nowMin >= startMin && nowMin < endMin) {
                return true;
            }
        }
        return false;
    }

    public static String buildWindowsText(List<Window> windows) {
        if (windows == null || windows.isEmpty()) windows = defaultWindows();
        StringBuilder sb = new StringBuilder("每周");
        for (int i = 0; i < windows.size(); i++) {
            Window w = windows.get(i);
            if (i > 0) sb.append("、");
            sb.append(DOW_CN[w.startDow()]).append(' ')
                    .append(w.startTime()).append('–')
                    .append(DOW_CN[w.endDow()]).append(' ').append(w.endTime());
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: 跑通单测**

```bash
cd delta-game && mvn -pl delta-player -am test -Dtest=WithdrawTimeWindowHelperTest
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add delta-game/delta-player/pom.xml \
  delta-game/delta-player/src/main/java/com/delta/player/util/WithdrawTimeWindowHelper.java \
  delta-game/delta-player/src/test/java/com/delta/player/util/WithdrawTimeWindowHelperTest.java
git commit -m "$(cat <<'EOF'
feat(player): 可配置提现时间窗口校验工具

提供 JSON 解析、左闭右开判定与默认周二/周六窗口文案。
EOF
)"
```

---

### Task 3: 合并双监听 + 确认时记待入账

**Files:**
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerIncomeServiceImpl.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/PlayerIncomeService.java`
- Modify: `delta-game/delta-admin/src/main/java/com/delta/admin/listener/SettlementEventListener.java`

**Interfaces:**
- Consumes: `settlement.delay_days`；`PlayerWallet.pendingBalance`；`Order.settleAvailableAt`
- Produces: 确认后 `settled=2`，pending 增加，无 INCOME；**仅** `SettlementEventListener` 监听确认事件

- [ ] **Step 1: 去掉 PlayerIncomeServiceImpl 的事件监听**

删除 `onOrderConfirmed` 及 `@EventListener`。`grep settleOrder(`：若仅被自身调用，删除旧立即入账实现，避免误用；接口方法可先留空或改为 Task 4 的新签名（本 Task 若改接口，需与 Task 4 对齐——推荐本 Task 只删 listener，保留类骨架）。

- [ ] **Step 2: 改 SettlementEventListener.creditWallet → creditPending**

```java
private void creditPending(Long playerId, BigDecimal income, Long orderId, String remark) {
    PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
    if (wallet == null) {
        playerWalletService.initWallet(playerId);
        wallet = playerWalletService.getByPlayerId(playerId);
    }
    if (wallet.getPendingBalance() == null) {
        wallet.setPendingBalance(BigDecimal.ZERO);
    }
    wallet.setPendingBalance(wallet.getPendingBalance().add(income));
    playerWalletService.updateById(wallet);
    log.info("记待入账: orderId={}, playerId={}, amount={}, remark={}",
            orderId, playerId, income, remark);
}
```

将所有 `creditWallet(...)` 调用改为 `creditPending(...)`。

幂等：

```java
if (order.getSettled() != null && (order.getSettled() == 1 || order.getSettled() == 2)) {
    log.warn("结算跳过: 订单已结算或待入账, orderId={}, settled={}", orderId, order.getSettled());
    return;
}
```

确认末尾（替换原 `settled=1` / `settleTime`）：

```java
int delayDays = Integer.parseInt(sysConfigService.getConfigValue("settlement.delay_days", "5"));
LocalDateTime confirmTime = order.getConfirmTime() != null ? order.getConfirmTime() : LocalDateTime.now();
order.setSettled(2);
order.setSettleAmount(playerTotalIncome);
order.setSettleAvailableAt(confirmTime.plusDays(delayDays));
orderService.updateById(order);
```

**不要**写 `INCOME` 流水。`order_player`：确认时写 `settleAmount`；`settledAt` 留空，入账任务再写。

- [ ] **Step 3: 验证**

1. 确认测试单 → `settled=2`，`settle_available_at≈now+5d`，`pending_balance`↑，`balance` 不变，无新 `INCOME`。  
2. 重复事件不双加 pending。

- [ ] **Step 4: Commit**

```bash
git add delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerIncomeServiceImpl.java \
  delta-game/delta-admin/src/main/java/com/delta/admin/listener/SettlementEventListener.java
git commit -m "$(cat <<'EOF'
feat(settlement): 确认后记待入账并去掉重复结算监听

订单确认只累加 pending_balance，合并双监听避免重复入账。
EOF
)"
```

---

### Task 4: 定时释放入账 + PlayerIncomeService

**Files:**
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/PlayerIncomeService.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerIncomeServiceImpl.java`
- Create: `delta-game/delta-admin/src/main/java/com/delta/admin/job/PendingSettlementTask.java`

**Interfaces:**
- Produces:
  - `int releaseDueSettlements(int limit)`
  - `void deductForOrderRefund(Order order, BigDecimal refundAmount)`（实现可在 Task 5 补全，本 Task 至少实现 release）

- [ ] **Step 1: 扩展接口**

```java
public interface PlayerIncomeService {
    /** @return 成功入账订单数 */
    int releaseDueSettlements(int limit);

    /**
     * 仲裁退款扣回：settled=2 先扣主打手本单剩余 settleAmount/pending，再扣 balance；
     * settled=1 只扣 balance。拟扣上限为 refundAmount（与现网 CsComplaintController 一致）。
     */
    void deductForOrderRefund(Order order, BigDecimal refundAmount);
}
```

- [ ] **Step 2: 实现 releaseDueSettlements**

```java
List<Order> due = orderService.list(new LambdaQueryWrapper<Order>()
        .eq(Order::getSettled, 2)
        .le(Order::getSettleAvailableAt, LocalDateTime.now())
        .orderByAsc(Order::getSettleAvailableAt)
        .last("LIMIT " + Math.max(1, limit)));
int ok = 0;
for (Order order : due) {
    if (releaseOne(order)) ok++;
}
return ok;
```

`releaseOne`（`@Transactional` 建议按单独立事务，或整批一事务；推荐 **每单独立事务** 以免一单失败拖垮整批——可用 `TransactionTemplate` 或自注入代理调用）：

1. 查 `order_player`：`role in (PRIMARY, TEAMMATE)` 且有 settleAmount（TEAMMATE 需 ACCEPTED）。  
2. 全部 settleAmount 为 0/null → `settled=1`，`settleTime=now`，return true。  
3. 预检每人 `pendingBalance >= settleAmount`；不足 → `log.error`，return false。  
4. 每人：pending-=，balance+=，totalIncome+=，写 INCOME，settledAt=now，发 `INCOME_SETTLED`。  
5. order：`settled=1`，`settleTime=now`。

- [ ] **Step 3: PendingSettlementTask**

```java
package com.delta.admin.job;

@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSettlementTask {
    private final PlayerIncomeService playerIncomeService;
    private final RedisService redisService;

    @Scheduled(cron = "0 0 * * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:pending_settlement", 50, TimeUnit.MINUTES)) return;
        try {
            int n = playerIncomeService.releaseDueSettlements(200);
            if (n > 0) log.info("待入账释放完成: {} 单", n);
        } finally {
            redisService.unlock("lock:task:pending_settlement");
        }
    }
}
```

`RedisService` 包路径与 `OrderAutoCancelTask` 相同：`com.delta.common.redis.service.RedisService`。

- [ ] **Step 4: 验证**

将测试单 `settle_available_at` 调到过去，调用 `releaseDueSettlements(10)` → balance/pending/settled/INCOME 正确。

- [ ] **Step 5: Commit**

```bash
git add delta-game/delta-player/src/main/java/com/delta/player/service/PlayerIncomeService.java \
  delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerIncomeServiceImpl.java \
  delta-game/delta-admin/src/main/java/com/delta/admin/job/PendingSettlementTask.java
git commit -m "$(cat <<'EOF'
feat(settlement): 每小时释放到期待入账到可提现余额

新增 PendingSettlementTask 与 PlayerIncomeService.releaseDueSettlements。
EOF
)"
```

---

### Task 5: 投诉退款先扣待入账

**Files:**
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerIncomeServiceImpl.java`
- Modify: `delta-game/delta-cs/src/main/java/com/delta/cs/controller/CsComplaintController.java`

**Interfaces:**
- Consumes: `deductForOrderRefund`
- 与现网一致：**只扣 `order.playerId` 主打手**（不自动扣队友，除非后续单独立项）

- [ ] **Step 1: 实现 deductForOrderRefund**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void deductForOrderRefund(Order order, BigDecimal refundAmount) {
    if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) return;
    if (order == null || order.getPlayerId() == null) return;
    Integer s = order.getSettled();
    if (s == null || (s != 1 && s != 2)) return;

    BigDecimal remain = refundAmount;
    Long playerId = order.getPlayerId();

    if (s == 2) {
        OrderPlayer primary = /* PRIMARY + playerId, 取最新一条 */;
        BigDecimal pendingPart = primary != null && primary.getSettleAmount() != null
                ? primary.getSettleAmount() : BigDecimal.ZERO;
        BigDecimal deductPending = remain.min(pendingPart);
        if (deductPending.compareTo(BigDecimal.ZERO) > 0) {
            PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
            // pendingBalance -= deductPending；primary.settleAmount -= deductPending
            // record REFUND，remark 含「待入账扣回」
            remain = remain.subtract(deductPending);
        }
    }

    if (remain.compareTo(BigDecimal.ZERO) > 0) {
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        BigDecimal balanceBefore = wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();
        BigDecimal deduction = remain.min(balanceBefore);
        if (deduction.compareTo(BigDecimal.ZERO) > 0) {
            wallet.setBalance(balanceBefore.subtract(deduction));
            playerWalletService.updateById(wallet);
            transactionService.record("REFUND", "PLAYER", playerId, deduction.negate(),
                    balanceBefore, wallet.getBalance(), order.getId(), null, null,
                    "投诉仲裁退款扣除收益，订单金额退款：" + refundAmount);
        }
    }
}
```

- [ ] **Step 2: CsComplaintController 改为委托**

注入 `PlayerIncomeService`，`refundPlayerIncomeIfSettled` 改为：

```java
playerIncomeService.deductForOrderRefund(order, refundAmount);
```

删除旧私有扣款实现。

- [ ] **Step 3: 验证**

待入账订单部分/全额退款 → pending 与 PRIMARY `settleAmount` 下降；超额扣 balance。

- [ ] **Step 4: Commit**

```bash
git add delta-game/delta-cs/src/main/java/com/delta/cs/controller/CsComplaintController.java \
  delta-game/delta-player/src/main/java/com/delta/player/service/impl/PlayerIncomeServiceImpl.java
git commit -m "$(cat <<'EOF'
feat(complaint): 仲裁退款优先扣待入账金额

settled=2 时先减订单剩余 settleAmount/pending，不足再扣可提现余额。
EOF
)"
```

---

### Task 6: 收益汇总 + 提现窗口 API

**Files:**
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/controller/PlayerEarningsController.java`
- Modify: `delta-game/delta-player/src/main/java/com/delta/player/controller/WithdrawController.java`

**Interfaces:**
- `GET /player/earnings/summary` → 增加 `pendingBalance`、`delayDays`
- `GET /player/withdraw/window` → `{ inWithdrawWindow, windowsText, windows, nextWindowHint }`
- `POST /player/withdraw` 窗外 → `BusinessException`

- [ ] **Step 1: summary**

注入 `SysConfigService`：

```java
data.put("pendingBalance", wallet != null && wallet.getPendingBalance() != null
        ? wallet.getPendingBalance() : BigDecimal.ZERO);
data.put("delayDays", Integer.parseInt(
        sysConfigService.getConfigValue("settlement.delay_days", "5")));
```

- [ ] **Step 2: GET /window（写在 `/{id}` 之前，避免路径冲突）**

```java
@GetMapping("/window")
public R<Map<String, Object>> window() {
    var windows = WithdrawTimeWindowHelper.parseWindows(
            sysConfigService.getConfigValue("withdraw.time_windows", ""));
    boolean in = WithdrawTimeWindowHelper.isInWindow(LocalDateTime.now(), windows);
    String text = WithdrawTimeWindowHelper.buildWindowsText(windows);
    Map<String, Object> data = new HashMap<>();
    data.put("inWithdrawWindow", in);
    data.put("windowsText", text);
    data.put("windows", windows);
    data.put("nextWindowHint", in ? "" : text);
    return R.ok(data);
}
```

- [ ] **Step 3: apply 增加窗口校验**（最低额之后）

```java
var windows = WithdrawTimeWindowHelper.parseWindows(
        sysConfigService.getConfigValue("withdraw.time_windows", ""));
if (!WithdrawTimeWindowHelper.isInWindow(LocalDateTime.now(), windows)) {
    throw new BusinessException("当前不在提现时间。可提现时间："
            + WithdrawTimeWindowHelper.buildWindowsText(windows));
}
```

- [ ] **Step 4: Commit**

```bash
git add delta-game/delta-player/src/main/java/com/delta/player/controller/PlayerEarningsController.java \
  delta-game/delta-player/src/main/java/com/delta/player/controller/WithdrawController.java
git commit -m "$(cat <<'EOF'
feat(player): 收益待入账字段与提现窗口接口校验

summary 返回 pendingBalance；提现申请受可配置时间窗口限制。
EOF
)"
```

---

### Task 7: 小程序打手端 UI

**Files:**
- Modify: `delta-mp/api/player.js`
- Modify: `delta-mp/constants/withdrawRules.js`
- Modify: `delta-mp/pages-player/earnings/index.vue`
- Modify: `delta-mp/pages-player/withdraw/index.vue`

- [ ] **Step 1: API**

```js
export const getWithdrawWindow = () => get('/player/withdraw/window', {}, { role: 'player' })
```

- [ ] **Step 2: 收益页**

在红卡累计收益下增加：

```html
<text class="pending-label">待入账 ¥{{ formatMoney(summary.pendingBalance) }}</text>
```

样式：`opacity: 0.85; font-size: 24rpx;`，沿用现有红卡，不换色板。

- [ ] **Step 3: 提现页**

- `getEarningsSummary`：展示 `balance`、`pendingBalance`、文案「确认后满 {{ delayDays }} 天转入可提现」  
- `getWithdrawWindow`：用 `windowsText` 覆盖规则「提现时间」；`inWithdrawWindow===false` 时禁用提交并 toast  
- 可提现仍只用 `balance`

- [ ] **Step 4: withdrawRules.js 默认文案**

```js
{
  label: '提现时间',
  text: '每周二 12:00–周三 12:00、周六 12:00–周日 12:00 可提交（以服务端校验为准）。'
}
```

- [ ] **Step 5: Commit**

```bash
git add delta-mp/api/player.js delta-mp/constants/withdrawRules.js \
  delta-mp/pages-player/earnings/index.vue delta-mp/pages-player/withdraw/index.vue
git commit -m "$(cat <<'EOF'
feat(mp): 打手端展示待入账并限制提现时间窗口

收益/提现页展示 pending；提现规则与按钮态对齐服务端窗口。
EOF
)"
```

---

### Task 8: 端到端验收

- [ ] **Step 1: Spec §7 清单**

| # | 场景 | 期望 |
|---|------|------|
| 1 | 确认订单 | settled=2，pending↑，balance 不变，无 INCOME |
| 2 | available_at 过去 + task | settled=1，balance↑，pending↓，有 INCOME |
| 3 | 待入账仲裁退款 | 先 pending 后 balance |
| 4 | 队友单 | 各自 pending/入账 |
| 5 | 窗外/窗内提现 | 拒绝 / 成功 |
| 6 | 旧 settled=1 | 不动 |
| 7 | 双监听 | pending 只加一次 |

- [ ] **Step 2: 单测**

```bash
cd delta-game && mvn -pl delta-player -am test -Dtest=WithdrawTimeWindowHelperTest
```

Expected: PASS

---

## Spec Coverage Self-Review

| Spec 项 | Task |
|---------|------|
| 延迟入账 settled=2 / pending | Task 3 |
| 小时任务 + LIMIT + 索引 | Task 1、4 |
| 投诉先 pending 后 balance | Task 5 |
| delay_days 可配置 | Task 1、3 |
| 历史不回溯 | Task 3 / 8 |
| 提现窗口可配置 | Task 2、6、7 |
| 收益+提现页展示 | Task 7 |
| 合并双监听 | Task 3 |
| 非目标（明细/排班） | 未排期 |

无 TBD；主打手扣款范围与现网一致已写明；命名全文统一。
