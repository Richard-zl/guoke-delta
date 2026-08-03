# 收益日报双口径 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收益日报同日展示「确认口径」与「已入账口径」两套统计，解决延迟入账导致近期确认为 0 的问题。

**Architecture:** `StatsMapper` 新增确认侧聚合/明细 SQL；保留现有入账侧 SQL；`AdminStatisticsController.incomeDaily` 按日合并两套数据并做字段兼容；`IncomeDaily.vue` 双汇总卡 + 双列表头 + 双明细。

**Tech Stack:** Java / MyBatis `@Select` / Vue 3 + Element Plus

**Spec:** `docs/superpowers/specs/2026-08-03-income-daily-dual-metrics-design.md`

## Global Constraints

- 确认：`status IN ('CONFIRMED','REVIEWED')` 且 `settled IN (1,2)`，按 `confirm_time` 归天。
- 已入账：`settled = 1` 且 `settle_time IS NOT NULL`，按 `settle_time` 归天（现状不变）。
- 旧字段 `orderCount/orderAmount/playerIncome/commissionIncome/orders` = 确认口径。
- 顶部：确认 4 卡 + 已入账 4 卡；`statDate` 键归一化为 `yyyy-MM-dd`。
- 不改仪表盘 GMV、不改延迟入账业务逻辑。

---

## File Structure

| 路径 | 职责 |
|------|------|
| `delta-common/.../mapper/StatsMapper.java` | 确认侧 stats/details SQL |
| `delta-admin/.../AdminStatisticsController.java` | 拼装双口径 list/summary |
| `delta-admin-ui/src/views/statistics/IncomeDaily.vue` | 双汇总 + 表列 + 展开明细 |

---

### Task 1: StatsMapper 确认侧 SQL

**Files:**
- Modify: `delta-game/delta-common/src/main/java/com/delta/common/mapper/StatsMapper.java`

- [ ] **Step 1: 在现有 incomeDaily 方法后新增**

```java
    @Select("SELECT DATE(confirm_time) AS statDate, COUNT(*) AS orderCount, " +
            "COALESCE(SUM(amount),0) AS orderAmount, " +
            "COALESCE(SUM(settle_amount),0) AS playerIncome, " +
            "COALESCE(SUM(amount - COALESCE(settle_amount, 0)),0) AS commissionIncome " +
            "FROM `order` " +
            "WHERE settled IN (1, 2) " +
            "AND confirm_time IS NOT NULL " +
            "AND status IN ('CONFIRMED','REVIEWED') " +
            "AND confirm_time >= #{start} AND confirm_time < #{end} " +
            "GROUP BY DATE(confirm_time) ORDER BY statDate")
    List<Map<String, Object>> incomeDailyConfirmStatsByRange(@Param("start") LocalDateTime start,
                                                             @Param("end") LocalDateTime end);

    @Select("SELECT DATE(o.confirm_time) AS statDate, " +
            "o.id, o.order_no AS orderNo, o.product_name AS productName, " +
            "o.amount, o.settle_amount AS playerIncome, " +
            "(o.amount - COALESCE(o.settle_amount, 0)) AS commissionIncome, " +
            "o.created_at AS createdAt, o.settle_time AS settleTime, o.settled AS settled, " +
            "u.nickname AS userNickname, p.nickname AS playerNickname " +
            "FROM `order` o " +
            "LEFT JOIN user u ON u.id = o.user_id " +
            "LEFT JOIN player p ON p.id = o.player_id " +
            "WHERE o.settled IN (1, 2) " +
            "AND o.confirm_time IS NOT NULL " +
            "AND o.status IN ('CONFIRMED','REVIEWED') " +
            "AND o.confirm_time >= #{start} AND o.confirm_time < #{end} " +
            "ORDER BY o.confirm_time DESC, o.id DESC")
    List<Map<String, Object>> incomeDailyConfirmOrderDetailsByRange(@Param("start") LocalDateTime start,
                                                                    @Param("end") LocalDateTime end);
```

- [ ] **Step 2: Commit**

```bash
git add delta-game/delta-common/src/main/java/com/delta/common/mapper/StatsMapper.java
git commit -m "feat(common): 收益日报增加确认日口径聚合查询"
```

---

### Task 2: Controller 双口径拼装

**Files:**
- Modify: `delta-game/delta-admin/src/main/java/com/delta/admin/controller/AdminStatisticsController.java`

- [ ] **Step 1: 重写 `incomeDaily`**

拉取确认 + 已入账两套 stats/details；用 `normalizeDateKey` 归一化 `statDate`；按日输出 confirm*/settled* 与兼容字段；summary 双合计 + 兼容=确认。

辅助方法可复用现有 `toLong`/`toBigDecimal`，新增：

```java
private String normalizeDateKey(Object value) {
    if (value == null) return "";
    if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
    if (value instanceof LocalDate d) return d.toString();
    if (value instanceof LocalDateTime dt) return dt.toLocalDate().toString();
    if (value instanceof java.util.Date d) {
        return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
    }
    String s = String.valueOf(value);
    return s.length() >= 10 ? s.substring(0, 10) : s;
}

private Map<String, Map<String, Object>> indexByStatDate(List<Map<String, Object>> rows) {
    Map<String, Map<String, Object>> map = new HashMap<>();
    if (rows == null) return map;
    for (Map<String, Object> row : rows) {
        String key = normalizeDateKey(row.get("statDate"));
        if (!key.isEmpty()) map.put(key, row);
    }
    return map;
}

private Map<String, List<Map<String, Object>>> groupDetailsByStatDate(List<Map<String, Object>> rows) {
    Map<String, List<Map<String, Object>>> map = new HashMap<>();
    if (rows == null) return map;
    for (Map<String, Object> row : rows) {
        String key = normalizeDateKey(row.get("statDate"));
        if (key.isEmpty()) continue;
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }
    return map;
}
```

每日 item 字段按 spec §3.1；summary 按 §3.2。

- [ ] **Step 2: Compile**

`cd delta-game && mvn -pl delta-admin -am -DskipTests compile`

- [ ] **Step 3: Commit**

```bash
git commit -am "feat(admin): 收益日报接口返回确认/已入账双口径"
```

---

### Task 3: IncomeDaily.vue UI

**Files:**
- Modify: `delta-admin-ui/src/views/statistics/IncomeDaily.vue`

- [ ] **Step 1: 顶部两行汇总卡**（确认 / 已入账），标签注明口径
- [ ] **Step 2: 主表分组列**「确认」「已入账」各订单数/金额/打手收入/抽成
- [ ] **Step 3: 展开区两段明细**（confirmOrders / settledOrders）；待入账 settleTime 显示「待入账」；settled=2 打标签
- [ ] **Step 4: Commit**

```bash
git commit -am "feat(admin-ui): 收益日报展示确认与已入账双口径"
```

---

### Task 4: 冒烟核对

- [ ] 编译 admin 模块通过
- [ ] 静态核对：确认 SQL 含 `settled IN (1,2)` + `confirm_time`；已入账仍用原方法
- [ ] 兼容字段等于 confirm*
