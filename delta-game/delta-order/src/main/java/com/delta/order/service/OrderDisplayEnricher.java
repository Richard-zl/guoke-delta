package com.delta.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单列表/详情展示字段填充（主接打手、辅助打手、用户昵称等）
 */
@Component
@RequiredArgsConstructor
public class OrderDisplayEnricher {

    private final OrderPlayerService orderPlayerService;
    private final CrossModuleMapper crossModuleMapper;

    /** 填充用户、主接打手、辅助打手展示字段 */
    public void enrich(Order order) {
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

    public void enrichList(List<Order> orders) {
        if (orders == null) return;
        orders.forEach(this::enrich);
    }

    /**
     * 打手「我的订单」：主接 OR 辅助（order.player_id2）OR order_player 已接受队友
     */
    public LambdaQueryWrapper<Order> buildPlayerOwnedWrapper(Long playerId, String status) {
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

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            w.eq(Order::getPlayerId, playerId)
                    .or()
                    .eq(Order::getPlayerId2, playerId);
            if (!teammateOrderIds.isEmpty()) {
                w.or().in(Order::getId, teammateOrderIds);
            }
        });
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper.orderByDesc(Order::getCreatedAt);
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
}
