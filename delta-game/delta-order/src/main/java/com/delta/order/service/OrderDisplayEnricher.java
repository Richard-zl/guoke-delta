package com.delta.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.dto.CategoryNameRow;
import com.delta.common.dto.ProductCategoryRow;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单列表/详情展示字段填充（主接打手、辅助打手、用户昵称等）
 */
@Component
@RequiredArgsConstructor
public class OrderDisplayEnricher {

    private final OrderPlayerService orderPlayerService;
    private final CrossModuleMapper crossModuleMapper;
    private final RefundRequestService refundRequestService;

    /** 填充用户、主接打手、辅助打手、分类展示字段 */
    public void enrich(Order order) {
        enrichWithoutRefund(order);
        fillCategory(List.of(order));
        fillRefundPending(order);
    }

    public void enrichList(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;
        orders.forEach(this::enrichWithoutRefund);
        fillCategory(orders);
        fillRefundPending(orders);
    }

    /** 仅填充用户/打手展示字段，退款标记由列表批量查询 */
    private void enrichWithoutRefund(Order order) {
        if (order == null) return;
        if (order.getUserId() != null) {
            String nick = crossModuleMapper.selectUserNickname(order.getUserId());
            order.setUserNickname((nick != null && !nick.isEmpty()) ? nick : "用户" + order.getUserId());
            order.setUserAvatar(crossModuleMapper.selectUserAvatar(order.getUserId()));
        }
        if (order.getPlayerId() != null) {
            String pNick = crossModuleMapper.selectPlayerNickname(order.getPlayerId());
            order.setPlayerName((pNick != null && !pNick.isEmpty()) ? pNick : "接单员" + order.getPlayerId());
            order.setPlayerAvatar(crossModuleMapper.selectPlayerAvatar(order.getPlayerId()));
        }
        Long teammateId = resolveTeammatePlayerId(order);
        if (teammateId != null) {
            order.setPlayerId2(teammateId);
            String tNick = crossModuleMapper.selectPlayerNickname(teammateId);
            order.setPlayerName2((tNick != null && !tNick.isEmpty()) ? tNick : "接单员" + teammateId);
        }
    }

    public void fillRefundPending(Order order) {
        if (order == null || order.getId() == null) return;
        order.setRefundPending(refundRequestService.isPending(order.getId()));
    }

    public void fillRefundPending(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;
        List<Long> ids = orders.stream().map(Order::getId).filter(id -> id != null).toList();
        Set<Long> pending = new HashSet<>(refundRequestService.listPendingOrderIds(ids));
        for (Order order : orders) {
            order.setRefundPending(order.getId() != null && pending.contains(order.getId()));
        }
    }

    /**
     * 打手「我的订单」：主接 OR 辅助（order.player_id2）OR order_player 已接受队友
     */
    public LambdaQueryWrapper<Order> buildPlayerOwnedWrapper(Long playerId, String status) {
        LambdaQueryWrapper<Order> wrapper = applyPlayerOwnedScope(new LambdaQueryWrapper<>(), playerId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreatedAt);
        return wrapper;
    }

    /**
     * 给任意 wrapper 追加「该打手参与」的范围条件（主接 / 辅助 / 已接受队友）。
     * 不含排序，可安全用于 count 统计。
     */
    public LambdaQueryWrapper<Order> applyPlayerOwnedScope(LambdaQueryWrapper<Order> wrapper, Long playerId) {
        List<Long> teammateOrderIds = orderPlayerService.list(
                new LambdaQueryWrapper<OrderPlayer>()
                        .eq(OrderPlayer::getPlayerId, playerId)
                        .eq(OrderPlayer::getRole, "TEAMMATE")
                        .eq(OrderPlayer::getStatus, "ACCEPTED")
                        .select(OrderPlayer::getOrderId))
                .stream()
                .map(OrderPlayer::getOrderId)
                .distinct()
                .toList();

        wrapper.and(w -> {
            w.eq(Order::getPlayerId, playerId)
                    .or()
                    .eq(Order::getPlayerId2, playerId);
            if (!teammateOrderIds.isEmpty()) {
                w.or().in(Order::getId, teammateOrderIds);
            }
        });
        return wrapper;
    }

    /** 辅助打手 ID：优先 order.player_id2，否则取首个已接受队友 */
    private Long resolveTeammatePlayerId(Order order) {
        if (order.getPlayerId2() != null) {
            return order.getPlayerId2();
        }
        OrderPlayer teammate = orderPlayerService.getOne(
                new LambdaQueryWrapper<OrderPlayer>()
                        .eq(OrderPlayer::getOrderId, order.getId())
                        .eq(OrderPlayer::getRole, "TEAMMATE")
                        .eq(OrderPlayer::getStatus, "ACCEPTED")
                        .orderByAsc(OrderPlayer::getAcceptedAt)
                        .last("LIMIT 1"));
        return teammate != null ? teammate.getPlayerId() : null;
    }

    /** 批量填一级分类、平台（子分类） */
    private void fillCategory(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;
        List<Long> productIds = orders.stream()
                .map(Order::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) return;

        List<ProductCategoryRow> rows = crossModuleMapper.selectProductCategories(productIds);
        Map<Long, ProductCategoryRow> byProduct = new HashMap<>();
        Set<Long> parentIds = new HashSet<>();
        for (ProductCategoryRow row : rows) {
            if (row.getProductId() == null) continue;
            byProduct.put(row.getProductId(), row);
            if (row.getParentId() != null && row.getParentId() > 0) {
                parentIds.add(row.getParentId());
            }
        }
        Map<Long, String> parentNames = parentIds.isEmpty()
                ? Map.of()
                : crossModuleMapper.selectCategoryNames(parentIds.stream().toList()).stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(CategoryNameRow::getId, CategoryNameRow::getName, (a, b) -> a));

        for (Order order : orders) {
            ProductCategoryRow row = byProduct.get(order.getProductId());
            if (row == null) {
                order.setPlatformName(OrderDispatchCopyRules.inferPlatform(order.getProductName()));
                continue;
            }
            String parentName = parentNames.get(row.getParentId());
            order.setCategoryName(OrderDispatchCopyRules.firstCategoryName(
                    row.getCategoryName(), row.getParentId(), parentName));
            order.setPlatformName(OrderDispatchCopyRules.platformName(
                    row.getCategoryName(), row.getParentId(), parentName, order.getProductName()));
            order.setDispatchDetailBlank(OrderDispatchCopyRules.blankDispatchDetail(
                    row.getCategoryName(), row.getParentId(), parentName));
        }
    }
}
