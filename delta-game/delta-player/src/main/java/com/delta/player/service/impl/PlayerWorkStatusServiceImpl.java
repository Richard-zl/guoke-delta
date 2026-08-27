package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.constant.PlayerOccupancyConstants;
import com.delta.common.domain.PageQuery;
import com.delta.common.dto.PlayerActiveOrderStats;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.service.PlayerWorkStatusCalculator;
import com.delta.common.util.MaxConcurrentConfigParser;
import com.delta.player.entity.Player;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerWorkStatusService;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批量查询订单占用，并按统一规则计算打手工作状态。
 */
@Service
@RequiredArgsConstructor
public class PlayerWorkStatusServiceImpl implements PlayerWorkStatusService {

    private final PlayerService playerService;
    private final CrossModuleMapper crossModuleMapper;
    private final SysConfigService sysConfigService;
    private final PlayerWorkStatusCalculator statusCalculator;

    @Override
    public void enrichOne(Player player) {
        if (player != null) {
            enrichBatch(Collections.singletonList(player));
        }
    }

    @Override
    public void enrichBatch(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        List<Long> playerIds = players.stream()
                .map(Player::getId)
                .toList();
        Map<Long, PlayerActiveOrderStats> statsByPlayerId =
                crossModuleMapper.batchSelectPlayerActiveOrderStats(playerIds).stream()
                        .collect(Collectors.toMap(
                                PlayerActiveOrderStats::getPlayerId,
                                Function.identity()));
        int maxConcurrent = getMaxConcurrent();
        LocalDateTime now = LocalDateTime.now();

        for (Player player : players) {
            PlayerActiveOrderStats stats = statsByPlayerId.get(player.getId());
            int activeOrders = stats == null || stats.getActiveOrders() == null
                    ? 0 : stats.getActiveOrders();
            int pendingAssignedOrders =
                    stats == null || stats.getPendingAssignedOrders() == null
                            ? 0 : stats.getPendingAssignedOrders();
            player.setActiveOrders(activeOrders);
            player.setPendingAssignedOrders(pendingAssignedOrders);
            player.setMaxConcurrent(maxConcurrent);
            player.setWorkStatus(statusCalculator.resolve(
                    player.getStatus(),
                    player.getFrozenUntil(),
                    player.getIsOnline(),
                    activeOrders,
                    pendingAssignedOrders,
                    maxConcurrent,
                    now).name());
        }
    }

    @Override
    public Page<Player> queryPage(
            PageQuery query,
            LambdaQueryWrapper<Player> wrapper,
            String workStatus) {
        if (workStatus == null || workStatus.isBlank()) {
            Page<Player> page = playerService.page(
                    new Page<>(query.getPageNum(), query.getPageSize()),
                    wrapper);
            enrichBatch(page.getRecords());
            return page;
        }

        List<Player> candidates = playerService.list(wrapper);
        enrichBatch(candidates);
        List<Player> filtered = candidates.stream()
                .filter(player -> workStatus.equals(player.getWorkStatus()))
                .toList();
        int from = Math.min(
                (query.getPageNum() - 1) * query.getPageSize(),
                filtered.size());
        int to = Math.min(from + query.getPageSize(), filtered.size());
        Page<Player> page = new Page<>(
                query.getPageNum(),
                query.getPageSize(),
                filtered.size());
        page.setRecords(filtered.subList(from, to));
        return page;
    }

    @Override
    public int getMaxConcurrent() {
        return MaxConcurrentConfigParser.parse(sysConfigService.getConfigValue(
                PlayerOccupancyConstants.MAX_ACTIVE_CONFIG_KEY,
                PlayerOccupancyConstants.DEFAULT_MAX_ACTIVE));
    }
}
