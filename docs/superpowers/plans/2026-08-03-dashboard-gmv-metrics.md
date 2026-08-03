# 仪表盘 GMV 经营指标 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Web 管理员仪表盘与客服工作台的经营金额/已支付订单数统一为「GMV（扣同日退款）/ 退款额 / 净成交」口径，并展示字段备注。

**Architecture:** 以 `payment`（`biz_type=ORDER`）为权威数据源；`StatsMapper` 提供原子聚合；纯函数 `DashboardMetricsCalculator` 派生 GMV/净成交；`DashboardMetricsService` 供 Admin/CS Controller 共用；前端三卡 + `el-tooltip` 备注。

**Tech Stack:** Java 17 / Spring Boot / MyBatis `@Select` / Vue 3 + Element Plus / JUnit 5

**Spec:** `docs/superpowers/specs/2026-08-03-dashboard-gmv-metrics-design.md`

## Global Constraints

- 金额只统计 `payment.biz_type = 'ORDER'`（排除打手押金）。
- 支付归属：`paid_at`；退款归属：`refund_time`；支付成功判定：`paid_at IS NOT NULL`（含后来 `REFUNDED`）。
- `GMV = paidGross - sameDayRefund`；`净成交 = paidGross - refundTotal`；同日退款 = 支付日与退款日为同一天。
- 覆盖：今日 + 累计 + 近 7 天趋势；Admin Web + CS Web。
- 不改：收益日报、小程序 CS、`statistics_daily`、订单状态分布、用户/打手副卡。
- CS「今日新增订单」仍按下单数（运营向）；金额三卡与 Admin 同口径。Admin「今日/总订单」改为已支付订单数。

---

## File Structure

| 路径 | 职责 |
|------|------|
| `delta-common/.../dto/DashboardMoneyMetrics.java` | 单窗口金额+订单 DTO |
| `delta-common/.../dto/DashboardTrendPoint.java` | 趋势单日点 |
| `delta-common/.../service/DashboardMetricsCalculator.java` | 纯函数派生 GMV/净成交 |
| `delta-common/.../service/DashboardMetricsService.java` | 组装日/累计/趋势 |
| `delta-common/.../mapper/StatsMapper.java` | 新增 payment 聚合 SQL |
| `delta-common/pom.xml` | 增加 `spring-boot-starter-test` |
| `delta-common/src/test/.../DashboardMetricsCalculatorTest.java` | 公式单测 |
| `delta-admin/.../AdminHomeController.java` | 接入新服务字段 |
| `delta-cs/.../CsDashboardController.java` | 今日金额三卡 |
| `delta-admin-ui/src/views/dashboard/index.vue` | Admin/CS UI + tooltip |

---

### Task 1: DTO + 纯函数计算器 + 单测

**Files:**
- Create: `delta-game/delta-common/src/main/java/com/delta/common/dto/DashboardMoneyMetrics.java`
- Create: `delta-game/delta-common/src/main/java/com/delta/common/service/DashboardMetricsCalculator.java`
- Create: `delta-game/delta-common/src/test/java/com/delta/common/service/DashboardMetricsCalculatorTest.java`
- Modify: `delta-game/delta-common/pom.xml`

**Interfaces:**
- Produces:
  - `DashboardMoneyMetrics` 字段：`paidOrderCount: Long`、`paidGross: BigDecimal`、`sameDayRefund: BigDecimal`、`refundAmount: BigDecimal`、`gmv: BigDecimal`、`netAmount: BigDecimal`
  - `DashboardMetricsCalculator.of(long paidOrderCount, BigDecimal paidGross, BigDecimal sameDayRefund, BigDecimal refundTotal): DashboardMoneyMetrics`

- [ ] **Step 1: 在 `delta-common/pom.xml` 的 `</dependencies>` 前增加测试依赖**

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 写失败单测**

```java
package com.delta.common.service;

import com.delta.common.dto.DashboardMoneyMetrics;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class DashboardMetricsCalculatorTest {

    @Test
    void sameDayAndCrossDayRefund_exampleFromSpec() {
        // 今日支付 1000，同日退 100，跨日退 200 → refundTotal=300
        DashboardMoneyMetrics m = DashboardMetricsCalculator.of(
                2L,
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                new BigDecimal("300.00"));
        assertEquals(0, new BigDecimal("900.00").compareTo(m.getGmv()));
        assertEquals(0, new BigDecimal("300.00").compareTo(m.getRefundAmount()));
        assertEquals(0, new BigDecimal("700.00").compareTo(m.getNetAmount()));
        assertEquals(2L, m.getPaidOrderCount());
    }

    @Test
    void nullAmounts_treatedAsZero() {
        DashboardMoneyMetrics m = DashboardMetricsCalculator.of(0L, null, null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo(m.getGmv()));
        assertEquals(0, BigDecimal.ZERO.compareTo(m.getNetAmount()));
    }

    @Test
    void identities_hold() {
        BigDecimal paid = new BigDecimal("500");
        BigDecimal sameDay = new BigDecimal("50");
        BigDecimal refund = new BigDecimal("120");
        DashboardMoneyMetrics m = DashboardMetricsCalculator.of(1L, paid, sameDay, refund);
        // 净成交 = paidGross - refundTotal
        assertEquals(0, paid.subtract(refund).compareTo(m.getNetAmount()));
        // 净成交 = GMV - crossDayRefund
        BigDecimal crossDay = refund.subtract(sameDay);
        assertEquals(0, m.getGmv().subtract(crossDay).compareTo(m.getNetAmount()));
    }
}
```

- [ ] **Step 3: 运行单测确认失败**

Run: `cd delta-game && mvn -pl delta-common -Dtest=DashboardMetricsCalculatorTest test`

Expected: 编译失败（类不存在）或测试失败。

- [ ] **Step 4: 实现 DTO 与计算器**

`DashboardMoneyMetrics.java`:

```java
package com.delta.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 经营金额窗口指标（GMV / 退款 / 净成交） */
@Data
public class DashboardMoneyMetrics {
    /** 已支付订单数（按 paid_at） */
    private Long paidOrderCount;
    /** 支付毛额 */
    private BigDecimal paidGross;
    /** 同日退款 */
    private BigDecimal sameDayRefund;
    /** 退款额（窗口内全部退款） */
    private BigDecimal refundAmount;
    /** GMV = paidGross - sameDayRefund */
    private BigDecimal gmv;
    /** 净成交 = paidGross - refundAmount */
    private BigDecimal netAmount;
}
```

`DashboardMetricsCalculator.java`:

```java
package com.delta.common.service;

import com.delta.common.dto.DashboardMoneyMetrics;
import java.math.BigDecimal;

/** 经营指标派生（纯函数，便于单测） */
public final class DashboardMetricsCalculator {
    private DashboardMetricsCalculator() {}

    public static DashboardMoneyMetrics of(Long paidOrderCount,
                                           BigDecimal paidGross,
                                           BigDecimal sameDayRefund,
                                           BigDecimal refundTotal) {
        BigDecimal gross = nz(paidGross);
        BigDecimal sameDay = nz(sameDayRefund);
        BigDecimal refund = nz(refundTotal);
        DashboardMoneyMetrics m = new DashboardMoneyMetrics();
        m.setPaidOrderCount(paidOrderCount == null ? 0L : paidOrderCount);
        m.setPaidGross(gross);
        m.setSameDayRefund(sameDay);
        m.setRefundAmount(refund);
        m.setGmv(gross.subtract(sameDay));
        m.setNetAmount(gross.subtract(refund));
        return m;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
```

- [ ] **Step 5: 再跑单测**

Run: `cd delta-game && mvn -pl delta-common -Dtest=DashboardMetricsCalculatorTest test`

Expected: `Tests run: 3, Failures: 0`

- [ ] **Step 6: Commit**

```bash
git add delta-game/delta-common/pom.xml \
  delta-game/delta-common/src/main/java/com/delta/common/dto/DashboardMoneyMetrics.java \
  delta-game/delta-common/src/main/java/com/delta/common/service/DashboardMetricsCalculator.java \
  delta-game/delta-common/src/test/java/com/delta/common/service/DashboardMetricsCalculatorTest.java
git commit -m "$(cat <<'EOF'
feat(common): 增加仪表盘 GMV 指标派生计算器

EOF
)"
```

---

### Task 2: StatsMapper payment 聚合 SQL

**Files:**
- Modify: `delta-game/delta-common/src/main/java/com/delta/common/mapper/StatsMapper.java`
- Create: `delta-game/delta-common/src/main/java/com/delta/common/dto/DashboardTrendPoint.java`

**Interfaces:**
- Consumes: 无
- Produces: Mapper 方法（见下）；`DashboardTrendPoint`：`date: String`、`paidOrderCount: Long`、`gmv: BigDecimal`、`refundAmount: BigDecimal`、`netAmount: BigDecimal`

- [ ] **Step 1: 在 `StatsMapper` 末尾（`countCsUnreadComplaints` 之后）增加方法**

```java
    // ========== 经营 GMV（payment 口径） ==========

    @Select("SELECT COALESCE(SUM(amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL AND DATE(paid_at) = #{date}")
    BigDecimal sumPaidGrossByDate(@Param("date") String date);

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL " +
            "AND DATE(paid_at) = #{date} AND DATE(refund_time) = #{date}")
    BigDecimal sumSameDayRefundByDate(@Param("date") String date);

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL AND DATE(refund_time) = #{date}")
    BigDecimal sumRefundAmountByDate(@Param("date") String date);

    @Select("SELECT COUNT(DISTINCT order_id) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL AND DATE(paid_at) = #{date}")
    Long countPaidOrdersByDate(@Param("date") String date);

    @Select("SELECT COALESCE(SUM(amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL")
    BigDecimal sumPaidGrossTotal();

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL " +
            "AND paid_at IS NOT NULL AND DATE(paid_at) = DATE(refund_time)")
    BigDecimal sumSameDayRefundTotal();

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL")
    BigDecimal sumRefundAmountTotal();

    @Select("SELECT COUNT(DISTINCT order_id) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL")
    Long countPaidOrdersTotal();

    @Select("SELECT DATE(paid_at) AS date, COUNT(DISTINCT order_id) AS paidOrderCount, " +
            "COALESCE(SUM(amount),0) AS paidGross " +
            "FROM payment WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL " +
            "AND paid_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(paid_at) ORDER BY date")
    List<Map<String, Object>> paidGrossTrend7Days();

    @Select("SELECT DATE(refund_time) AS date, COALESCE(SUM(refund_amount),0) AS refundAmount " +
            "FROM payment WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL " +
            "AND refund_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(refund_time) ORDER BY date")
    List<Map<String, Object>> refundTrend7Days();

    @Select("SELECT DATE(paid_at) AS date, COALESCE(SUM(refund_amount),0) AS sameDayRefund " +
            "FROM payment WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL AND paid_at IS NOT NULL " +
            "AND DATE(paid_at) = DATE(refund_time) " +
            "AND paid_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(paid_at) ORDER BY date")
    List<Map<String, Object>> sameDayRefundTrend7Days();
```

- [ ] **Step 2: 新建 `DashboardTrendPoint.java`**

```java
package com.delta.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 近 N 日经营趋势单日点 */
@Data
public class DashboardTrendPoint {
    private String date;
    private Long paidOrderCount;
    private BigDecimal gmv;
    private BigDecimal refundAmount;
    private BigDecimal netAmount;
}
```

- [ ] **Step 3: Commit**

```bash
git add delta-game/delta-common/src/main/java/com/delta/common/mapper/StatsMapper.java \
  delta-game/delta-common/src/main/java/com/delta/common/dto/DashboardTrendPoint.java
git commit -m "$(cat <<'EOF'
feat(common): 增加 payment 口径 GMV 聚合查询

EOF
)"
```

---

### Task 3: DashboardMetricsService

**Files:**
- Create: `delta-game/delta-common/src/main/java/com/delta/common/service/DashboardMetricsService.java`

**Interfaces:**
- Consumes: `StatsMapper` 新方法；`DashboardMetricsCalculator.of(...)`
- Produces:
  - `metricsForDate(String date): DashboardMoneyMetrics`
  - `metricsTotal(): DashboardMoneyMetrics`
  - `trendLast7Days(): List<DashboardTrendPoint>`（含无数据日补 0，共最多 7 个自然日，从 `CURDATE()-6` 到今天）

- [ ] **Step 1: 实现 Service**

```java
package com.delta.common.service;

import com.delta.common.dto.DashboardMoneyMetrics;
import com.delta.common.dto.DashboardTrendPoint;
import com.delta.common.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardMetricsService {
    private final StatsMapper statsMapper;

    public DashboardMoneyMetrics metricsForDate(String date) {
        return DashboardMetricsCalculator.of(
                statsMapper.countPaidOrdersByDate(date),
                statsMapper.sumPaidGrossByDate(date),
                statsMapper.sumSameDayRefundByDate(date),
                statsMapper.sumRefundAmountByDate(date));
    }

    public DashboardMoneyMetrics metricsTotal() {
        return DashboardMetricsCalculator.of(
                statsMapper.countPaidOrdersTotal(),
                statsMapper.sumPaidGrossTotal(),
                statsMapper.sumSameDayRefundTotal(),
                statsMapper.sumRefundAmountTotal());
    }

    public List<DashboardTrendPoint> trendLast7Days() {
        Map<String, BigDecimal> paidGross = toDecimalMap(statsMapper.paidGrossTrend7Days(), "date", "paidGross");
        Map<String, Long> paidOrders = toLongMap(statsMapper.paidGrossTrend7Days(), "date", "paidOrderCount");
        Map<String, BigDecimal> refunds = toDecimalMap(statsMapper.refundTrend7Days(), "date", "refundAmount");
        Map<String, BigDecimal> sameDay = toDecimalMap(statsMapper.sameDayRefundTrend7Days(), "date", "sameDayRefund");

        List<DashboardTrendPoint> points = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            DashboardMoneyMetrics m = DashboardMetricsCalculator.of(
                    paidOrders.getOrDefault(key, 0L),
                    paidGross.getOrDefault(key, BigDecimal.ZERO),
                    sameDay.getOrDefault(key, BigDecimal.ZERO),
                    refunds.getOrDefault(key, BigDecimal.ZERO));
            DashboardTrendPoint p = new DashboardTrendPoint();
            p.setDate(key);
            p.setPaidOrderCount(m.getPaidOrderCount());
            p.setGmv(m.getGmv());
            p.setRefundAmount(m.getRefundAmount());
            p.setNetAmount(m.getNetAmount());
            points.add(p);
        }
        return points;
    }

    private Map<String, BigDecimal> toDecimalMap(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyField);
            if (k == null) continue;
            map.put(String.valueOf(k), toBigDecimal(row.get(valueField)));
        }
        return map;
    }

    private Map<String, Long> toLongMap(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyField);
            if (k == null) continue;
            Object v = row.get(valueField);
            map.put(String.valueOf(k), v == null ? 0L : ((Number) v).longValue());
        }
        return map;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }
}
```

注意：`trendLast7Days` 里 `paidGrossTrend7Days()` 被调用了两次——实现时缓存到局部变量一次即可（plan 要求最终代码只查一次）：

```java
List<Map<String, Object>> paidRows = statsMapper.paidGrossTrend7Days();
Map<String, BigDecimal> paidGross = toDecimalMap(paidRows, "date", "paidGross");
Map<String, Long> paidOrders = toLongMap(paidRows, "date", "paidOrderCount");
```

- [ ] **Step 2: 编译 common**

Run: `cd delta-game && mvn -pl delta-common -DskipTests compile`

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add delta-game/delta-common/src/main/java/com/delta/common/service/DashboardMetricsService.java
git commit -m "$(cat <<'EOF'
feat(common): 增加 DashboardMetricsService 统一经营口径

EOF
)"
```

---

### Task 4: AdminHomeController 接入

**Files:**
- Modify: `delta-game/delta-admin/src/main/java/com/delta/admin/controller/AdminHomeController.java`

**Interfaces:**
- Consumes: `DashboardMetricsService.metricsForDate` / `metricsTotal` / `trendLast7Days`
- Produces: dashboard Map 新字段（见 Step 1）；兼容字段 `todayAmount`/`totalAmount`/`yesterdayAmount` = 对应 `gmv`

- [ ] **Step 1: 改写 Controller**

将类改为注入 `DashboardMetricsService` + 保留 `StatsMapper`（待办/用户等仍用 Mapper）：

```java
package com.delta.admin.controller;

import com.delta.common.domain.R;
import com.delta.common.dto.DashboardMoneyMetrics;
import com.delta.common.mapper.StatsMapper;
import com.delta.common.service.DashboardMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/home")
@RequiredArgsConstructor
public class AdminHomeController {
    private final StatsMapper statsMapper;
    private final DashboardMetricsService dashboardMetricsService;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<>();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        DashboardMoneyMetrics todayM = dashboardMetricsService.metricsForDate(today);
        DashboardMoneyMetrics yestM = dashboardMetricsService.metricsForDate(yesterday);
        DashboardMoneyMetrics totalM = dashboardMetricsService.metricsTotal();

        // ====== 今日核心（经营口径） ======
        data.put("todayOrders", todayM.getPaidOrderCount());
        data.put("todayGmv", todayM.getGmv());
        data.put("todayRefundAmount", todayM.getRefundAmount());
        data.put("todayNetAmount", todayM.getNetAmount());
        // 兼容：旧字段语义升级为 GMV
        data.put("todayAmount", todayM.getGmv());
        data.put("todayNewUsers", statsMapper.countUsersByDate(today));
        data.put("todayNewPlayers", statsMapper.countPlayersByDate(today));

        // ====== 昨日对比 ======
        data.put("yesterdayOrders", yestM.getPaidOrderCount());
        data.put("yesterdayGmv", yestM.getGmv());
        data.put("yesterdayRefundAmount", yestM.getRefundAmount());
        data.put("yesterdayNetAmount", yestM.getNetAmount());
        data.put("yesterdayAmount", yestM.getGmv());
        data.put("yesterdayNewUsers", statsMapper.countUsersByDate(yesterday));

        // ====== 待办事项 ======
        data.put("pendingComplaints", statsMapper.countPendingComplaints());
        data.put("pendingWithdraws", statsMapper.countPendingWithdraws());
        data.put("pendingAssign", statsMapper.countPendingAssignOrders());
        data.put("inProgress", statsMapper.countInProgressOrders());

        // ====== 累计统计 ======
        data.put("totalUsers", statsMapper.countTotalUsers());
        data.put("totalPlayers", statsMapper.countTotalPlayers());
        data.put("totalOrders", totalM.getPaidOrderCount());
        data.put("totalGmv", totalM.getGmv());
        data.put("totalRefundAmount", totalM.getRefundAmount());
        data.put("totalNetAmount", totalM.getNetAmount());
        data.put("totalAmount", totalM.getGmv());

        // ====== 近7天经营趋势 ======
        data.put("orderTrend", dashboardMetricsService.trendLast7Days());

        // ====== 订单状态分布 ======
        data.put("statusDistribution", statsMapper.orderStatusDistribution());

        return R.ok(data);
    }
}
```

- [ ] **Step 2: 编译 admin 模块**

Run: `cd delta-game && mvn -pl delta-admin -am -DskipTests compile`

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add delta-game/delta-admin/src/main/java/com/delta/admin/controller/AdminHomeController.java
git commit -m "$(cat <<'EOF'
feat(admin): 仪表盘接入 payment 口径 GMV 三指标

EOF
)"
```

---

### Task 5: CsDashboardController 接入

**Files:**
- Modify: `delta-game/delta-cs/src/main/java/com/delta/cs/controller/CsDashboardController.java`

**Interfaces:**
- Consumes: `DashboardMetricsService.metricsForDate(today)`
- Produces: `todayGmv` / `todayRefundAmount` / `todayNetAmount`；`todayAmount` = `todayGmv`（兼容）

- [ ] **Step 1: 注入 Service，替换今日成交额**

在字段中增加：

```java
private final DashboardMetricsService dashboardMetricsService;
```

将原先：

```java
result.put("todayAmount", statsMapper.sumOrderAmountByDateRange(todayStart, todayEnd));
```

替换为：

```java
DashboardMoneyMetrics todayMoney = dashboardMetricsService.metricsForDate(LocalDate.now().toString());
result.put("todayGmv", todayMoney.getGmv());
result.put("todayRefundAmount", todayMoney.getRefundAmount());
result.put("todayNetAmount", todayMoney.getNetAmount());
result.put("todayAmount", todayMoney.getGmv()); // 兼容旧前端
```

并补充 import：

```java
import com.delta.common.dto.DashboardMoneyMetrics;
import com.delta.common.service.DashboardMetricsService;
```

`todayOrders`（今日新增订单，按下单）保持不变。

- [ ] **Step 2: 编译 cs 模块**

Run: `cd delta-game && mvn -pl delta-cs -am -DskipTests compile`

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add delta-game/delta-cs/src/main/java/com/delta/cs/controller/CsDashboardController.java
git commit -m "$(cat <<'EOF'
feat(cs): 工作台今日成交改为 GMV/退款/净成交

EOF
)"
```

---

### Task 6: Admin / CS 前端展示与备注

**Files:**
- Modify: `delta-admin-ui/src/views/dashboard/index.vue`

**Interfaces:**
- Consumes: API 字段 `todayGmv`、`todayRefundAmount`、`todayNetAmount`、`totalGmv`、`totalRefundAmount`、`totalNetAmount`、`orderTrend[].{date,paidOrderCount,gmv,refundAmount,netAmount}`；昨日镜像字段

- [ ] **Step 1: 在 `<script setup>` 增加口径文案常量与小组件用法**

在 script 中增加：

```js
const METRIC_TIPS = {
  gmv: '支付成功金额（按支付日），已扣除支付日与退款日均为该统计日的退款；不含待支付/未付款取消；不扣跨日退款',
  refund: '统计日内完成退款的金额（可含历史支付单）',
  net: '支付毛额减去当日全部退款；因跨日退款可能小于 GMV，甚至为负',
  paidOrders: '支付成功过的订单数（含后来全额/部分退款的）'
}
```

标签写法示例（今日 GMV）：

```vue
<div class="stat-label">
  今日 GMV（已扣同日退款）
  <el-tooltip :content="METRIC_TIPS.gmv" placement="top">
    <el-icon class="tip-icon"><QuestionFilled /></el-icon>
  </el-tooltip>
</div>
```

确保从 `@element-plus/icons-vue` 引入 `QuestionFilled`（与现有 icon 引入方式一致）。

- [ ] **Step 2: 改 Admin「今日数据」区块**

- 「今日订单」文案改为「今日已支付订单」，值仍用 `stats.todayOrders`（后端已是已支付数），加 `METRIC_TIPS.paidOrders`
- 将原「今日成交额」一卡拆为三卡（`:span` 按一行排得下调整，可用 `span=6` 换行）：
  - `stats.todayGmv`（兼容 fallback：`stats.todayAmount`）
  - `stats.todayRefundAmount`
  - `stats.todayNetAmount`
- 昨日对比：GMV/订单保留；退款与净成交显示昨日绝对值（`yesterdayRefundAmount` / `yesterdayNetAmount`）

- [ ] **Step 3: 改 Admin「累计数据」**

- 「总订单数」→「累计已支付订单」+ tip
- 「总成交额」→ 三卡：`totalGmv` / `totalRefundAmount` / `totalNetAmount`

- [ ] **Step 4: 改近 7 天趋势表**

```vue
<el-table-column prop="date" label="日期" width="120" />
<el-table-column prop="paidOrderCount" label="已支付订单" width="100">
  <template #default="{ row }">{{ row.paidOrderCount ?? row.orders ?? '-' }}</template>
</el-table-column>
<el-table-column label="GMV">
  <template #header>
    <span>GMV
      <el-tooltip :content="METRIC_TIPS.gmv" placement="top">
        <el-icon class="tip-icon"><QuestionFilled /></el-icon>
      </el-tooltip>
    </span>
  </template>
  <template #default="{ row }">¥{{ formatAmount(row.gmv ?? row.amount) }}</template>
</el-table-column>
<el-table-column label="退款额">
  <template #default="{ row }">¥{{ formatAmount(row.refundAmount) }}</template>
</el-table-column>
<el-table-column label="净成交">
  <template #default="{ row }">¥{{ formatAmount(row.netAmount) }}</template>
</el-table-column>
```

趋势条宽度：优先用 `row.paidOrderCount`，旧 `row.orders` 作 fallback。检查 `barWidth` / `max` 计算处同步。

- [ ] **Step 5: 改 CS「平台概览」今日成交**

将单卡「今日成交额」改为三张 mini 卡：`todayGmv` / `todayRefundAmount` / `todayNetAmount`，同样加 tip。`span` 调整为容纳三卡（可与待审核提现等分两行）。

- [ ] **Step 6: 加少量样式**

```css
.tip-icon {
  margin-left: 4px;
  cursor: help;
  vertical-align: middle;
  color: var(--el-text-color-secondary);
}
```

- [ ] **Step 7: 本地打开 Admin 仪表盘与 CS 工作台，目视确认卡片与 tip**

- [ ] **Step 8: Commit**

```bash
git add delta-admin-ui/src/views/dashboard/index.vue
git commit -m "$(cat <<'EOF'
feat(admin-ui): 仪表盘展示 GMV/退款/净成交并注明口径

EOF
)"
```

---

### Task 7: 联调验收清单

**Files:** 无代码变更（或仅修 bug）

- [ ] **Step 1: 按 spec §5 场景核对（有测试库则造数）**

| # | 场景 | 期望 |
|---|------|------|
| 1 | 仅 `CANCELLED` 未付款 | 不进 GMV/退款/已支付订单 |
| 2 | 支付未退 | GMV=支付额，退款=0，净=支付额 |
| 3 | 同日全额退 | GMV=0，退款=支付额，净=0 |
| 4 | 同日部分退 | GMV=支付-实退 |
| 5 | 跨日退 | 支付日 GMV 不因次日退款减少；退款日记入退款额 |
| 6 | `PLAYER_DEPOSIT` | 不计入 |
| 7 | Admin 与 CS 同日 GMV/退款/净 一致 | 一致 |
| 8 | 恒等式 | `net = paidGross - refund`；`net = gmv - crossDay` |

- [ ] **Step 2: 跑计算器单测回归**

Run: `cd delta-game && mvn -pl delta-common -Dtest=DashboardMetricsCalculatorTest test`

Expected: PASS

- [ ] **Step 3: 若联调发现问题，修完后单独 commit；无问题则本 Task 无需 commit**

---

## Spec Coverage Checklist

| Spec 要求 | Task |
|-----------|------|
| payment 权威源 + biz_type=ORDER | Task 2 |
| GMV 扣同日退、跨日只进退款额 | Task 1 + 3 |
| 三卡 + 备注 | Task 6 |
| 已支付订单数按 paid_at | Task 2–4 |
| 今日+累计+7 日趋势 | Task 3–4、6 |
| Admin + CS Web | Task 4–6 |
| 不改收益日报/小程序/日批 | （无对应 Task） |
| 恒等式与场景验收 | Task 1、7 |
| CANCELLED vs REFUNDED 说明落地 | Task 2 SQL + Task 7 |

## Self-Review Notes

- 无 TBD/TODO 占位。
- 字段名前后一致：`gmv` / `refundAmount` / `netAmount` / `paidOrderCount`。
- `trendLast7Days` 实现须只调用一次 `paidGrossTrend7Days()`。
- `DATE(paid_at)` 在 MySQL 下返回类型可能是 `java.sql.Date`，`String.valueOf(k)` 一般为 `yyyy-MM-dd`，与 `LocalDate.toString()` 对齐；若出现时区偏移，在 Task 7 发现后改为格式化 `toLocalDate()`。
