# 打手转介绍分成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理员把已注册客户绑定到打手并设置介绍比例后，该客户下单确认时从俱乐部抽成中记介绍费，打手只读查看。

**Architecture:** 绑定表 `player_referral` 存在打手模块。纯计算放在 `PlayerReferralRules`。订单确认时由 `SettlementEventListener` 写入订单快照并加入待入账。到期释放和退款扣回放在 `PlayerIncomeServiceImpl`。后台只给管理员，打手端只读。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、Flyway、Vue3 管理端、uni-app 打手端、JUnit 5。

## Global Constraints

- 介绍费从该单俱乐部抽成中出。接单打手池 = 订单金额 − 俱乐部抽成，不受介绍费影响。
- 介绍费 = min(订单金额 × 介绍比例, 俱乐部抽成)，四舍五入到分。
- 比例大于 0、不超过 100%，最多两位小数。库存为小数，5% 存 0.0500。
- 一个用户同时只有一条有效绑定。已绑定则拒绝，并提示当前介绍人。
- 查找按用户 ID、手机号、昵称精确匹配。0 个拒绝，多个必须先选择。
- 改比例和解绑只影响之后确认的订单。
- 介绍人状态为正常才记介绍费。冻结保留绑定但不记。离职自动解绑。
- 介绍费确认时进待入账，到期转入可提现时写正数流水 `REFERRAL`。退款当时写负数流水。
- 多次退款累计不超过该单介绍费，按实际扣到的金额累计，扣不到不再补扣。
- 仅管理员可改绑定。打手端不能改。客服端不提供入口。
- 不回溯已确认订单，不支持未注册手机号占位。

---

### Task 1: 分成与比例规则

**Files:**
- Create: `delta-game/delta-player/src/main/java/com/delta/player/service/PlayerReferralRules.java`
- Test: `delta-game/delta-player/src/test/java/com/delta/player/service/PlayerReferralRulesTest.java`

**Interfaces:**
- Produces: `PlayerReferralRules.normalizeRate(BigDecimal percent)`, `settle(amount, commissionRate, referralRate)`, `clawbackDue(...)`, `remaining(...)`, `bindBlockReason(String status)`

- [ ] 先写失败测试，再实现规则，运行 `mvn -pl delta-player -am test -Dtest=PlayerReferralRulesTest`

### Task 2: 绑定表、订单快照、绑定服务

**Files:**
- Create: `delta-game/delta-admin/src/main/resources/db/migration/V20261009__player_referral.sql`
- Create: `PlayerReferral`、`PlayerReferralMapper`、`PlayerReferralService`、`PlayerReferralServiceImpl`

**Interfaces:**
- Produces: `listActive`、`bind`、`updateRate`、`unbind`、`unbindAll`、`activeForSettlement`
- 有效行 `active_key=0`，解绑后 `active_key=id`。唯一键 `(user_id, active_key)`。

### Task 3: 确认结算、释放、退款

**Files:**
- Modify: `Order.java`
- Modify: `SettlementEventListener.java`
- Modify: `PlayerIncomeServiceImpl.java`

确认时调用 `activeForSettlement`。释放时把介绍费和接单收入一起从待入账转入可提现，流水类型 `REFERRAL`。退款按比例扣介绍人，不改变接单打手原有扣回。

### Task 4: 管理端

**Files:**
- Modify: `AdminPlayerController.java`、`PlayerList.vue`、`business.js`
- 离职成功时 `unbindAll`

接口：查找、列表、新增、改比例、解绑。均带操作日志。

### Task 5: 打手端只读

**Files:**
- Create: `PlayerReferralController.java`
- Create: `delta-mp/pages-player/referral/list.vue`、`detail.vue`
- Modify: `pages.json`、`mine/index.vue`、`api/player.js`、`earnings/detail.vue`

收益明细把 `REFERRAL` 显示为「转介绍分成」。
