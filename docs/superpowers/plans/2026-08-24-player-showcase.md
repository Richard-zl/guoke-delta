# 打手风采 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地运营精选「打手风采」橱窗：用户端可浏览/听语音/指定下单，打手端可录音传图，后台可上墙排序，非 ACTIVE 自动下架。

**Architecture:** 新增 `player_showcase` 表（一人一卡）+ `player` 语音/图库字段 + `review.hide_in_showcase`。规则集中在 `PlayerShowcaseRules` / `PlayerShowcaseService`。用户只读走公开 GET；打手维护走 `/player/showcase/me/**`；后台走 `/admin/player/showcase/**`。前端复用指定打手与 `designatedPlayerId`，不改派单引擎。

**Tech Stack:** Java 17 / Spring Boot / MyBatis-Plus / JUnit 5 / uni-app Vue3 / Element Plus

**Spec:** `docs/superpowers/specs/2026-08-24-player-showcase-design.md`

## Global Constraints

- 上架硬条件：`player.status = ACTIVE` 且 `intro_voice_url` 非空。
- 非 ACTIVE（冻结/驳回/删除）立即把对应风采 `status` 置 0；再上墙必须运营手动上架。
- 排序纯手动 `sort_order` 升序；离线/满载仍展示，仅禁用指定。
- 评价隐藏只过滤风采详情，不改 `avg_rating`。
- 审核期 `isUnderReview===true` 隐藏全部用户端风采入口。
- 客服端不做风采后台。不做视频、关注、预约离线。
- 指定资格与现网一致：ACTIVE + `isOnline=1` + 进行中单 < `order.max_active_per_player`。
- `Player` 字段以 `Player.java` 为准：`skillTags`、`avgRating`、`completeRate`、`isOnline`、`completedOrders`、`activeOrders`。
- 配置键：`order.max_active_per_player`。
- 公开详情路径用 `GET /player/showcase/detail/{playerId}`，避免 `permitAll` 误放行 `/me`。

---

## File Structure

| 路径 | 职责 |
|---|---|
| `sql/player_showcase.sql` | DDL |
| `delta-player/.../entity/PlayerShowcase.java` | 上墙卡 |
| `delta-player/.../service/PlayerShowcaseRules.java` | 纯规则 |
| `delta-player/.../service/PlayerShowcaseService.java` | 组装 VO、自动下架 |
| `delta-player/.../controller/PlayerShowcaseController.java` | 用户公开 + 打手自己 |
| `delta-admin/.../controller/AdminPlayerShowcaseController.java` | 后台 CRUD |
| `delta-order/.../entity/Review.java` | `hideInShowcase` |
| `SecurityConfig.java` | 公开接口放行 |
| `delta-mp/pages/showcase/*` | 用户列表/详情 |
| `delta-mp/pages/index/index.vue` | 首页区块 |
| `delta-mp/pages/order/create.vue` | 预选 + 查看风采 |
| `delta-mp/pages-player/showcase/index.vue` | 打手录音图库 |
| `delta-admin-ui/src/views/player/PlayerShowcase.vue` | 后台上墙 |

---

### Task 1: DDL

**Files:**
- Create: `sql/player_showcase.sql`
- Modify: `delta_game.sql`（追加同样 DDL）

- [ ] **Step 1: 写入并执行 SQL**

```sql
ALTER TABLE `player`
  ADD COLUMN `intro_voice_url` varchar(512) NOT NULL DEFAULT '' COMMENT '语音介绍URL' AFTER `is_online`,
  ADD COLUMN `intro_voice_seconds` int NOT NULL DEFAULT 0 COMMENT '语音秒数' AFTER `intro_voice_url`,
  ADD COLUMN `highlight_images` varchar(4096) NOT NULL DEFAULT '' COMMENT '高光图库URL逗号分隔最多9张' AFTER `intro_voice_seconds`;

ALTER TABLE `review`
  ADD COLUMN `hide_in_showcase` tinyint NOT NULL DEFAULT 0 COMMENT '1=风采详情隐藏' AFTER `images`;

CREATE TABLE `player_showcase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `player_id` bigint NOT NULL COMMENT '绑定打手',
  `cover_url` varchar(512) NOT NULL DEFAULT '' COMMENT '运营封面，空则用头像',
  `tagline` varchar(32) NOT NULL DEFAULT '' COMMENT '一句话标签',
  `bio` varchar(512) NOT NULL DEFAULT '' COMMENT '运营简介',
  `selected_images` varchar(4096) NOT NULL DEFAULT '' COMMENT '运营勾选的高光URL逗号分隔',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '越小越靠前',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0下架 1上架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_player_id` (`player_id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打手风采上墙卡';
```

- [ ] **Step 2: Commit** `feat: add player showcase schema`

---

### Task 2: 实体 + 上架规则 + 单测

**Files:**
- Create: `PlayerShowcase.java` / `PlayerShowcaseMapper.java` / `PlayerShowcaseRules.java` / `PlayerShowcaseService(Impl).java`
- Create: `delta-game/delta-player/src/test/java/com/delta/player/service/PlayerShowcaseRulesTest.java`
- Modify: `Player.java` 增加 `introVoiceUrl`、`introVoiceSeconds`、`highlightImages`、`onWall`（exist=false）
- Modify: `Review.java` 增加 `hideInShowcase`

**Rules API:**

```java
public final class PlayerShowcaseRules {
    public static String publishBlockReason(String playerStatus, String voiceUrl, String tagline) {
        if (!"ACTIVE".equals(playerStatus)) return "仅 ACTIVE 可上架";
        if (voiceUrl == null || voiceUrl.isBlank()) return "该打手没有语音介绍，无法上架";
        if (tagline == null || tagline.isBlank()) return "上架需要填写一句话标签";
        if (tagline.length() > 8) return "一句话标签最多8字";
        return null;
    }
    public static String filterSelectedImages(String selectedCsv, String galleryCsv) { /* 只保留仍在图库中的 URL */ }
    public static boolean canDesignate(String status, Integer isOnline, int activeOrders, int maxActive) {
        return "ACTIVE".equals(status) && isOnline != null && isOnline == 1 && activeOrders < maxActive;
    }
    public static String designateBlockReason(String status, Integer isOnline, int activeOrders, int maxActive) {
        if (!"ACTIVE".equals(status)) return "该打手暂未展示";
        if (isOnline == null || isOnline != 1) return "当前离线，无法指定";
        if (activeOrders >= maxActive) return "当前接单已满，无法指定";
        return null;
    }
}
```

测试覆盖：非 ACTIVE 不能上架、无语音不能上架、勾选图删除后过滤、离线/满载不能指定。

Run: `mvn -pl delta-game/delta-player -am test -Dtest=PlayerShowcaseRulesTest`

`unpublishByPlayerId`：将该 player 的 showcase `status` 更新为 0。

- [ ] **Step: Commit** `feat: add player showcase domain and publish rules`

---

### Task 3: 打手端接口

- `GET /player/showcase/me` → `{ onWall, introVoiceUrl, introVoiceSeconds, highlightImages[] }`
- `PUT /player/showcase/me/voice` `{ url, seconds }` 秒数必须 15–60；重录不改上架状态
- `PUT /player/showcase/me/gallery` `{ urls: string[] }` 最多 9；删除后 `filterSelectedImages` 写回勾选，不下架

- [ ] **Step: Commit** `feat: player self showcase voice and gallery APIs`

---

### Task 4: 用户公开接口 + 评价过滤

- `GET /player/showcase/active?pageNum&pageSize` 仅 status=1 且 player ACTIVE，按 sort_order
- `GET /player/showcase/detail/{playerId}` 未上架/非 ACTIVE → 失败文案「该打手暂未展示」
- Card/Detail 字段：`playerId, nickname, avatar, coverUrl, tagline, avgRating, completedOrders, completeRate, skillTags[], isOnline, activeOrders, maxConcurrent, canDesignate, designateBlockReason`
- Detail 额外：`bio, introVoiceUrl, introVoiceSeconds, highlightImages[]`
- `completedOrders`/`activeOrders` 用 `CrossModuleMapper.selectPlayerCompletedOrders` / `selectPlayerActiveOrders`
- `maxConcurrent` 读 `order.max_active_per_player`
- `coverUrl` 空则用 avatar

SecurityConfig：

```java
.requestMatchers(HttpMethod.GET, "/player/showcase/active").permitAll()
.requestMatchers(HttpMethod.GET, "/player/showcase/detail/*").permitAll()
```

`ReviewController.byPlayer` 增加 `forShowcase`；true 时过滤 `hideInShowcase=1`。

- [ ] **Step: Commit** `feat: public player showcase list and detail APIs`

---

### Task 5: 后台接口 + 冻结自动下架

- `GET /admin/player/showcase/list`
- `POST /admin/player/showcase`（一人一卡，重复报「该打手已有风采卡」）
- `PUT /admin/player/showcase/{id}`
- `PUT /admin/player/showcase/{id}/status` `{ status: 0|1 }`，上架必须通过 `publishBlockReason`
- `PUT /admin/review/{id}/hide-in-showcase` `{ hide }`

`AdminPlayerController.freeze` / `reject` / `updateStatus`（非 ACTIVE）以及 `CsPlayerController.freeze` 之后调用 `unpublishByPlayerId`。

- [ ] **Step: Commit** `feat: admin showcase APIs and auto-unpublish on freeze`

---

### Task 6: 小程序用户端

- Create `delta-mp/api/showcase.js`（`auth: false`）
- Create `pages/showcase/list.vue`、`detail.vue`
- `pages.json` 主包追加两页，标题「打手风采」「打手详情」
- 首页分类宫格与热门推荐之间插入区块；`v-if="!isUnderReview && showcaseList.length"`
- 详情：`InnerAudioContext` 播语音；评价 `getReviewsByPlayer(id, { forShowcase:true })`
- 可指定：`setStorageSync('designatePlayerId', id)` 后 `navigateTo /pages/category/index`
- `from=picker`：按钮「就选 TA」，storage 后 `navigateBack`

- [ ] **Step: Commit** `feat: user showcase list, detail, and home section`

---

### Task 7: 下单预选 + 弹层入口

- `UserPlayerController.availablePlayers` 批量填 `onWall`
- `create.vue` `onLoad` 读 `designatePlayerId`，打开指定并预选，然后 removeStorage
- 预选人离线/满载：toast「该打手暂不可指定」，不选中
- `p.onWall && !isUnderReview` 显示「查看风采」→ detail `from=picker`

- [ ] **Step: Commit** `feat: designate picker showcase entry and preselect from storage`

---

### Task 8: 打手端风采资料

- `pages-player/showcase/index.vue`；`pages.json` 分包登记
- `pages-player/mine/index.vue` 在「系统通知」上方加「风采资料」
- `RecorderManager` format mp3；15–60 秒；`upload` 后 PUT voice
- 图库复用 `chooseAndUpload`，≤9，长按删除需确认

- [ ] **Step: Commit** `feat: player showcase profile with voice and gallery`

---

### Task 9: 管理后台页面

把 `/player` 改成带 children：`list`（admin+cs）+ `showcase`（仅 admin）。核对原 `/player` 跳转改为 `/player/list`。

`PlayerShowcase.vue` 对齐 `views/system/BannerList.vue`：表格 + 弹窗（选打手、封面、tagline、bio、排序、勾选高光、评价隐藏开关、上架）。

`business.js` 增加对应 API。

- [ ] **Step: Commit** `feat: admin player showcase CMS page`

---

### Task 10: 回归清单

1. 无上墙：首页无区块
2. 上墙后首页可见、详情可播语音
3. 离线按钮置灰「当前离线，无法指定」
4. 可指定：进分类 → 下单页预勾
5. 弹层查看风采 → 就选 TA 回到下单并选中
6. 冻结后墙上消失；解冻不自动回墙
7. 重录语音立即生效且仍上墙
8. 审核期用户端风采全隐
9. 评价隐藏只影响风采详情
10. 客服菜单无「打手风采」

Run: `mvn -pl delta-game/delta-player -am test -Dtest=PlayerShowcaseRulesTest`

---

## Self-Review

- 规格中的入口、转化、上墙规则、录音图库、评价隐藏、审核期、自动下架均有 Task。
- 无 TBD。公开详情刻意避开 `/me`。
- storage key 全链路为 `designatePlayerId`；上架 status 为 0/1。
