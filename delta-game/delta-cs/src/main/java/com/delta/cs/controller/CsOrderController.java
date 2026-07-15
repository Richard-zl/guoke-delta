package com.delta.cs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.mapper.StatsMapper;
import com.delta.common.util.QueryDateUtils;
import com.delta.order.dto.OrderListQuery;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderProgress;
import com.delta.order.service.OrderDisplayEnricher;
import com.delta.order.service.OrderProgressService;
import com.delta.order.service.OrderQueryBuilder;
import com.delta.order.service.OrderService;
import com.delta.common.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/cs/order")
@RequiredArgsConstructor
public class CsOrderController {
    private final OrderService orderService;
    private final OrderProgressService orderProgressService;
    private final OrderDisplayEnricher orderDisplayEnricher;
    private final StatsMapper statsMapper;

    @GetMapping("/list")
    public R<Page<Order>> list(PageQuery query,
                               @RequestParam(value = "status", required = false) String status,
                               @RequestParam(value = "statusIn", required = false) String statusIn,
                               @RequestParam(value = "orderNo", required = false) String orderNo,
                               @RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "userId", required = false) Long userId,
                               @RequestParam(value = "playerId", required = false) Long playerId,
                               @RequestParam(value = "productId", required = false) Long productId,
                               @RequestParam(value = "createdAtStart", required = false) String createdAtStart,
                               @RequestParam(value = "createdAtEnd", required = false) String createdAtEnd,
                               @RequestParam(value = "unassigned", required = false) Boolean unassigned) {
        OrderListQuery listQuery = new OrderListQuery();
        listQuery.setOrderNo(orderNo);
        listQuery.setKeyword(keyword);
        listQuery.setStatus(status);
        listQuery.setStatusIn(statusIn);
        listQuery.setUserId(userId);
        listQuery.setPlayerId(playerId);
        listQuery.setProductId(productId);
        listQuery.setCreatedAtStart(QueryDateUtils.parseStart(createdAtStart));
        listQuery.setCreatedAtEnd(QueryDateUtils.parseEnd(createdAtEnd));
        listQuery.setUnassigned(unassigned);

        Page<Order> page = orderService.page(
                new Page<>(query.getPageNum(), query.getPageSize()),
                OrderQueryBuilder.build(listQuery));
        orderDisplayEnricher.enrichList(page.getRecords());
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<Order> detail(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order != null) orderDisplayEnricher.enrich(order);
        return R.ok(order);
    }

    @GetMapping("/{id}/progress")
    public R<List<OrderProgress>> progress(@PathVariable Long id) {
        return R.ok(orderProgressService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderProgress>()
                .eq(OrderProgress::getOrderId, id).orderByAsc(OrderProgress::getCreatedAt)));
    }

    @PostMapping("/{id}/assign/{playerId}")
    public R<Void> assign(@PathVariable Long id, 
                          @PathVariable Long playerId,
                          @RequestParam(required = false) Long playerId2) {
        orderService.assignOrder(id, playerId, playerId2, "CS", SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{id}/refund")
    public R<Void> refund(@PathVariable Long id) {
        orderService.csCancelOrder(id, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        orderService.manualConfirmOrder(id, SecurityUtils.getUserId(), "CS");
        return R.ok();
    }

    /** 客服查看用户消费榜单（复用统计 Mapper） */
    @GetMapping("/spending-rank")
    public R<java.util.List<java.util.Map<String, Object>>> spendingRank(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return R.ok(statsMapper.userSpendingRank(Math.min(limit, 200)));
    }
}
