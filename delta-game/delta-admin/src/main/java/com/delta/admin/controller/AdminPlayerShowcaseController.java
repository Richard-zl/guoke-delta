package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.order.entity.Review;
import com.delta.order.service.ReviewService;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerShowcase;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerShowcaseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/player/showcase")
@RequiredArgsConstructor
public class AdminPlayerShowcaseController {
    private final PlayerShowcaseService playerShowcaseService;
    private final PlayerService playerService;
    private final ReviewService reviewService;
    private final CrossModuleMapper crossModuleMapper;

    @GetMapping("/list")
    public R<Page<Map<String, Object>>> list(PageQuery query) {
        Page<PlayerShowcase> page = playerShowcaseService.page(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<PlayerShowcase>().orderByAsc(PlayerShowcase::getSortOrder)
                        .orderByDesc(PlayerShowcase::getId));
        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toRow).toList());
        return R.ok(voPage);
    }

    @OpLog(module = "player-showcase", operation = "保存风采卡")
    @PostMapping
    public R<Void> create(@RequestBody PlayerShowcase card) {
        card.setId(null);
        playerShowcaseService.saveCard(card);
        return R.ok();
    }

    @OpLog(module = "player-showcase", operation = "更新风采卡")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody PlayerShowcase card) {
        card.setId(id);
        playerShowcaseService.saveCard(card);
        return R.ok();
    }

    @OpLog(module = "player-showcase", operation = "上下架风采")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody StatusBody body) {
        playerShowcaseService.updateStatus(id, body.getStatus());
        return R.ok();
    }

    /** 新增风采时读取与公开风采一致的真实指标口径。 */
    @GetMapping("/player/{playerId}/metrics")
    public R<Map<String, Object>> playerMetrics(@PathVariable Long playerId) {
        Player player = playerService.getById(playerId);
        if (player == null) {
            return R.fail("打手不存在");
        }
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("realAvgRating", player.getAvgRating());
        metrics.put("realCompletedOrders",
                crossModuleMapper.selectPlayerCompletedOrders(playerId));
        metrics.put("realCompleteRate", player.getCompleteRate());
        return R.ok(metrics);
    }

    @GetMapping("/{playerId}/reviews")
    public R<Page<Review>> reviews(@PathVariable Long playerId, PageQuery query) {
        return R.ok(reviewService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Review>().eq(Review::getPlayerId, playerId)
                        .orderByDesc(Review::getCreatedAt)));
    }

    @OpLog(module = "player-showcase", operation = "风采隐藏评价")
    @PutMapping("/review/{reviewId}/hide")
    public R<Void> hideReview(@PathVariable Long reviewId, @RequestBody HideBody body) {
        Review patch = new Review();
        patch.setId(reviewId);
        patch.setHideInShowcase(Boolean.TRUE.equals(body.getHide()) ? 1 : 0);
        reviewService.updateById(patch);
        return R.ok();
    }

    private Map<String, Object> toRow(PlayerShowcase card) {
        Player player = playerService.getById(card.getPlayerId());
        Map<String, Object> row = new HashMap<>();
        row.put("id", card.getId());
        row.put("playerId", card.getPlayerId());
        row.put("coverUrl", card.getCoverUrl());
        row.put("tagline", card.getTagline());
        row.put("bio", card.getBio());
        row.put("displayRating", card.getDisplayRating());
        row.put("displayCompletedOrders", card.getDisplayCompletedOrders());
        row.put("displayCompleteRate", card.getDisplayCompleteRate());
        row.put("selectedImages", card.getSelectedImages());
        row.put("sortOrder", card.getSortOrder());
        row.put("status", card.getStatus());
        row.put("updatedAt", card.getUpdatedAt());
        if (player != null) {
            row.put("nickname", player.getNickname());
            row.put("avatar", player.getAvatar());
            row.put("playerStatus", player.getStatus());
            row.put("hasVoice", player.getIntroVoiceUrl() != null && !player.getIntroVoiceUrl().isBlank());
            row.put("introVoiceUrl", player.getIntroVoiceUrl());
            row.put("highlightImages", player.getHighlightImages());
            row.put("avgRating", player.getAvgRating());
            row.put("realAvgRating", player.getAvgRating());
            row.put("realCompletedOrders",
                    crossModuleMapper.selectPlayerCompletedOrders(player.getId()));
            row.put("realCompleteRate", player.getCompleteRate());
            row.put("isOnline", player.getIsOnline());
        }
        return row;
    }

    @Data
    public static class StatusBody {
        private Integer status;
    }

    @Data
    public static class HideBody {
        private Boolean hide;
    }
}
