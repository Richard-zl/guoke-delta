# 打手风采（第一期）信息架构与交互规格

> 日期：2026-08-24
> 状态：产品逻辑已确认，本文件锁定 IA / 线框 / 交互
> 范围：用户端小程序 + 打手端小程序 + 管理后台（仅 admin）
> 依据：产品逻辑稿「打手风采（第一期）」

---

## 0. 决议摘要

| # | 决议 |
|---|------|
| 1 | 运营精选橱窗，一人一卡，绑定真实 `player.id` |
| 2 | 上架硬条件：`player.status = ACTIVE` **且** 已有语音介绍 |
| 3 | 非 ACTIVE（冻结/驳回/删除）立即自动下架；重新上墙必须运营再点上架 |
| 4 | 排序纯手动；离线/满载仍展示，只打标、禁用「指定 TA」 |
| 5 | 首页横滑 + 列表 + 详情；下单指定弹层对已上墙的人增加「查看风采」 |
| 6 | 「指定 TA 下单」→ 现有选商品 → 确认订单预勾该打手，可取消/换人 |
| 7 | 评价全量可翻，运营可「风采中隐藏」（只影响风采详情） |
| 8 | 高光图：打手传图库，运营勾选对外；无勾选则详情不展示该块 |
| 9 | 微信审核期（`isUnderReview`）隐藏全部风采入口 |
| 10 | 客服端第一期不新增风采工作台 |

不做：全员市场、短视频、动态/关注/点赞、预约离线、全站删评价。

---

## 1. 信息架构

用户端：首页横滑 → 列表 → 详情 →（指定 TA）分类/商品 → 确认订单。确认订单指定弹层可回详情，「就选 TA」返回订单页。

打手端：接单员中心 → 风采资料（录音 + 图库）。

后台：打手管理 → 打手风采（列表 + 编辑弹窗，仅 admin）。

### 1.1 新增页面

| 端 | 路径 | 标题 | 说明 |
|---|---|---|---|
| 用户 | `pages/showcase/list` | 打手风采 | 已上墙全量，手动排序 |
| 用户 | `pages/showcase/detail` | 打手详情 | `?playerId=` ，可选 `from=picker` |
| 打手 | `pages-player/showcase/index` | 风采资料 | 录音 + 图库 + 上墙只读状态 |
| 后台 | `/player/showcase` | 打手风采 | 挂在「打手管理」下，仅 admin |

不新增 Tab。首页只加一个区块。

### 1.2 首页插入位置

现有顺序：搜索 → Banner → 公告 → 分类宫格 → 热门推荐。

风采区块插在 **分类宫格与热门推荐之间**。无上墙数据时整块不渲染。审核期不渲染。

---

## 2. 用户端线框

### 2.1 首页区块

- 标题「打手风采」+「更多」
- 卡片横滑：封面、昵称、一句话标签、状态点（绿=在线可接，灰=离线，橙=满载）
- 卡片宽约 220rpx；点卡片进详情；点「更多」进列表
- 首页最多取排序前 8 条

### 2.2 列表页

- 左图右文：封面、昵称+状态徽章、标签、评分+完成单
- 离线/满载仍在原位
- 下拉刷新；分页 pageSize=20

### 2.3 详情页（固定顺序，底部吸底）

1. 封面大图 16:9 + 昵称 + 一句话标签
2. 评分 / 完成单 / 完成率 / 在线状态
3. 技能标签（`skill_tags`）
4. 语音介绍（播放器）
5. 简介（运营文案）
6. 高光图（无勾选则整块隐藏）
7. 老板评价（分页；过滤风采隐藏）
8. 吸底：「指定 TA 下单」；`from=picker` 时为「就选 TA」

### 2.4 指定弹层

现有行保留。已上墙的人增加「查看风采」。未上墙不加。审核期不加。

---

## 3. 关键交互

### 3.1 指定 TA 下单（人 → 商品 → 单）

1. 可接单：`ACTIVE` + 在线 + 未满载。否则 disabled：离线「当前离线，无法指定」；满载「当前接单已满，无法指定」
2. 可点：把 `designatePlayerId` 写入 storage，`navigateTo` `pages/category/index`
3. 选商品 → 详情 → 确认订单
4. 确认订单 `onLoad` 读取 storage，打开指定开关并预选，然后清除 storage
5. 预选时已离线/满载：toast「该打手暂不可指定」，开关开但未选中
6. 用户可关掉指定或换人。提交仍走 `designatedPlayerId`

从弹层「就选 TA」：`navigateBack` 并选中，不跳商品。

### 3.2 语音 / 高光 / 评价

- 语音：同时只播一条；展示秒数；失败 toast「语音加载失败」；不自动播放
- 高光：`previewImage`；只显示运营勾选且仍在图库中的 URL
- 评价：倒序分页；过滤 `hide_in_showcase=1`；评分仍用 `avg_rating` 不因隐藏重算；无可见评价显示「暂无评价」

### 3.3 审核期 / 下架 / 空墙

| 场景 | 行为 |
|---|---|
| `isUnderReview` | 首页区块、列表、详情、弹层入口全部隐藏；详情直达 toast 后返回 |
| 墙上 0 人 | 首页区块不渲染 |
| 详情已下架/非 ACTIVE | 「该打手暂未展示」+ 回列表 |
| 打手删掉被勾选的图 | 该图从详情消失，上架状态不变 |

---

## 4. 打手端

入口：接单员中心，「系统通知」上方，文案「风采资料」。

- 上墙状态只读：已上墙 / 未上墙
- 语音：录制/重录/试听；15-60 秒；短于 15 秒不可保存
- 已上墙时重录不改变上架状态
- 图库最多 9 张；长按删除；不能勾选对外、不能改封面/简介/排序

---

## 5. 管理后台

菜单位置：打手管理 → 打手风采（仅 admin）。交互对齐轮播图：列表 + 编辑弹窗。

列表列：ID、打手、上架、排序、有无语音、封面、更新时间、操作。

编辑字段：

| 字段 | 规则 |
|---|---|
| 打手 | 必选，一人一卡，创建后不可改绑 |
| 封面 | 选填，空则用户端用头像 |
| 一句话标签 | 上架时必填，不超过 8 字 |
| 简介 | 选填，不超过 200 字 |
| 排序 | 越小越靠前 |
| 高光勾选 | 多选打手图库；可空 |
| 评价隐藏 | 仅影响风采详情 |
| 只读 | 状态、语音、评分、单量、在线 |

上架失败：「该打手没有语音介绍，无法上架」「仅 ACTIVE 可上架」。冻结自动下架；恢复后须再点上架。

---

## 6. 文案

| 位置 | 文案 |
|---|---|
| 首页区块标题 | 打手风采 |
| 首页更多 | 更多 |
| 详情主按钮 | 指定 TA 下单 |
| 弹层回填 | 就选 TA |
| 弹层入口 | 查看风采 |
| 离线禁用 | 当前离线，无法指定 |
| 满载禁用 | 当前接单已满，无法指定 |
| 下架详情 | 该打手暂未展示 |
| 打手菜单 | 风采资料 |
| 后台菜单 | 打手风采 |

---

## 7. 与现有模块衔接

- 指定资格与 `UserPlayerController.availablePlayers` / `OrderServiceImpl.assignOrder` 一致：`ACTIVE`、在线、进行中订单小于 `order.max_active_per_player`
- 下单字段沿用 `CreateOrderRequest.designatedPlayerId`
- 评价查询沿用 `/order/review/player/{playerId}`，增加隐藏过滤
- 技能标签沿用 `player.skill_tags`
- 上传沿用 `POST /common/file/upload`
- 审核期沿用 `useAuditMode().isUnderReview`

---

## 8. 实现任务拆分

> 执行时按 Task 顺序做。规则代码先写单测。公开详情路径必须用 `/player/showcase/detail/{id}`，避免 `permitAll` 误放行 `/me`。

### 全局约束

- 上架：`player.status = ACTIVE` 且 `intro_voice_url` 非空
- 非 ACTIVE 立即下架（`player_showcase.status=0`），再上墙须运营手动开
- 排序 `sort_order` 升序；离线/满载仍展示，只禁指定
- 评价隐藏不改 `avg_rating`
- 审核期隐藏全部用户端入口
- 指定资格：ACTIVE + `isOnline=1` + 进行中 < `order.max_active_per_player`
- `Player.java` 字段：`skillTags`、`avgRating`、`completeRate`、`isOnline`、`completedOrders`、`activeOrders`
- storage key：`designatePlayerId`

### Task 1 — DDL

文件：`sql/player_showcase.sql`，并同步追加到 `delta_game.sql`。

```sql
ALTER TABLE `player`
  ADD COLUMN `intro_voice_url` varchar(512) NOT NULL DEFAULT '' COMMENT '语音介绍URL' AFTER `is_online`,
  ADD COLUMN `intro_voice_seconds` int NOT NULL DEFAULT 0 COMMENT '语音秒数' AFTER `intro_voice_url`,
  ADD COLUMN `highlight_images` varchar(4096) NOT NULL DEFAULT '' COMMENT '高光图库URL逗号分隔最多9张' AFTER `intro_voice_seconds`;

ALTER TABLE `review`
  ADD COLUMN `hide_in_showcase` tinyint NOT NULL DEFAULT 0 COMMENT '1=风采详情隐藏' AFTER `images`;

CREATE TABLE `player_showcase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `player_id` bigint NOT NULL,
  `cover_url` varchar(512) NOT NULL DEFAULT '',
  `tagline` varchar(32) NOT NULL DEFAULT '',
  `bio` varchar(512) NOT NULL DEFAULT '',
  `selected_images` varchar(4096) NOT NULL DEFAULT '',
  `sort_order` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0下架 1上架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_player_id` (`player_id`),
  KEY `idx_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='打手风采上墙卡';
```

### Task 2 — 规则 + 实体 + 单测

新建 `PlayerShowcaseRules`：

- `publishBlockReason(status, voiceUrl, tagline)`
- `filterSelectedImages(selectedCsv, galleryCsv)`
- `canDesignate` / `designateBlockReason`

单测 `PlayerShowcaseRulesTest`：非 ACTIVE、无语音、标签>8 字、勾选图删除、离线、满载。

`mvn -pl delta-game/delta-player -am test -Dtest=PlayerShowcaseRulesTest`

`PlayerShowcaseService.unpublishByPlayerId` 将该打手卡 `status` 置 0。

### Task 3 — 打手接口

- `GET /player/showcase/me`
- `PUT /player/showcase/me/voice` `{url, seconds}` 秒数 15–60；重录不下架
- `PUT /player/showcase/me/gallery` `{urls[]}` 最多 9 张；删除后过滤 `selected_images`

### Task 4 — 用户公开接口

- `GET /player/showcase/active`
- `GET /player/showcase/detail/{playerId}` 未上架/非 ACTIVE →「该打手暂未展示」
- VO 含：封面回退头像、评分、完成单、完成率、技能标签、在线、进行中、`maxConcurrent`、`canDesignate`、`designateBlockReason`、语音、过滤后的高光
- `SecurityConfig` 仅放行上述两个 GET
- `ReviewController.byPlayer` 增加 `forShowcase`，为 true 时过滤 `hideInShowcase=1`
- 完成单/进行中：`CrossModuleMapper.selectPlayerCompletedOrders` / `selectPlayerActiveOrders`

### Task 5 — 后台接口 + 自动下架

- CRUD：`/admin/player/showcase/list|POST|PUT /{id}|PUT /{id}/status`
- `PUT /admin/review/{id}/hide-in-showcase`
- 一人一卡；上架必须过 `publishBlockReason`
- `AdminPlayerController.freeze/reject/updateStatus` 与 `CsPlayerController.freeze` 之后调用 `unpublishByPlayerId`

### Task 6 — 小程序用户端

- `delta-mp/api/showcase.js`（`auth: false`）
- `pages/showcase/list.vue`、`detail.vue`；`pages.json` 主包登记
- 首页分类与热门之间插区块；`!isUnderReview && list.length`
- 可指定：`setStorageSync('designatePlayerId')` → `/pages/category/index`
- `from=picker`：按钮「就选 TA」后 `navigateBack`

### Task 7 — 下单预选 + 弹层

- `availablePlayers` 批量打 `onWall`
- `create.vue` 读 storage 预选后清除；离线/满载 toast 不选中
- 已上墙且非审核期显示「查看风采」

### Task 8 — 打手端页面

- `pages-player/showcase/index.vue`；菜单「风采资料」放在系统通知上方
- `RecorderManager` mp3，15–60 秒；图库复用 `chooseAndUpload`

### Task 9 — 管理后台页

- `/player` 拆 children：`list`（admin+cs）、`showcase`（仅 admin）
- `PlayerShowcase.vue` 对齐 `BannerList.vue`
- 原 `/player` 跳转改为 `/player/list`

### Task 10 — 回归

1. 无上墙：首页无区块
2. 上墙后可播语音
3. 离线按钮置灰
4. 指定后下单页预勾
5. 弹层「就选 TA」回填
6. 冻结下架，解冻不自动回墙
7. 重录语音立即生效
8. 审核期全隐
9. 评价隐藏只影响风采
10. 客服无风采菜单
