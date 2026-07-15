package com.delta.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.utils.ImageListUtils;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderProgress;
import com.delta.order.service.OrderDisplayEnricher;
import com.delta.order.service.OrderProgressService;
import com.delta.order.service.OrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 打手端订单查询与操作（列表/进度更新/开始/完成）
 */
@RestController
@RequestMapping("/player/work")
@RequiredArgsConstructor
public class PlayerWorkController {
    private final OrderService orderService;
    private final OrderProgressService orderProgressService;
    private final OrderDisplayEnricher orderDisplayEnricher;

    @GetMapping("/available")
    public R<Page<Order>> available(PageQuery query) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PAID")
                .orderByDesc(Order::getCreatedAt);
        return R.ok(orderService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }

    @GetMapping("/my")
    public R<Page<Order>> myOrders(PageQuery query, @RequestParam(required = false) String status) {
        Long playerId = SecurityUtils.getUserId();
        LambdaQueryWrapper<Order> wrapper = orderDisplayEnricher.buildPlayerOwnedWrapper(playerId, status);
        Page<Order> page = orderService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        orderDisplayEnricher.enrichList(page.getRecords());
        return R.ok(page);
    }

    @PostMapping("/{id}/start")
    public R<Void> start(@PathVariable Long id) {
        orderService.startOrder(id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id, @RequestBody(required = false) ProgressRequest req) {
        orderService.completeOrder(id, SecurityUtils.getUserId(), req != null ? req.getImages() : null);
        return R.ok();
    }

    /**
     * 打手更新订单进度
     */
    @PostMapping("/{id}/progress")
    public R<Void> updateProgress(@PathVariable Long id, @RequestBody ProgressRequest req) {
        Long playerId = SecurityUtils.getUserId();
        Order order = orderService.getById(id);
        if (order == null) return R.fail("订单不存在");
        if (!playerId.equals(order.getPlayerId())) return R.fail("无权操作");
        OrderProgress progress = new OrderProgress();
        progress.setOrderId(id);
        progress.setType("PROGRESS_UPDATE");
        progress.setOperatorType("PLAYER");
        progress.setOperatorId(playerId);
        progress.setContent(req.getContent());
        progress.setImages(ImageListUtils.normalize(req.getImages()));
        progress.setCreatedAt(LocalDateTime.now());
        orderProgressService.save(progress);
        return R.ok();
    }

    @Data
    public static class ProgressRequest {
        private String content;
        private String images;
    }
}
