package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.dto.PlayerActiveOrderStats;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.service.PlayerWorkStatusCalculator;
import com.delta.player.entity.Player;
import com.delta.player.service.PlayerService;
import com.delta.system.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerWorkStatusServiceImplTest {

    @Mock
    private PlayerService playerService;
    @Mock
    private CrossModuleMapper crossModuleMapper;
    @Mock
    private SysConfigService sysConfigService;

    private PlayerWorkStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlayerWorkStatusServiceImpl(
                playerService,
                crossModuleMapper,
                sysConfigService,
                new PlayerWorkStatusCalculator());
        when(sysConfigService.getConfigValue("order.max_active_per_player", "1"))
                .thenReturn("1");
    }

    @Test
    void enrichBatchFillsZeroStatsAndResolvesStatus() {
        Player available = player(1L, 1);
        Player full = player(2L, 1);
        when(crossModuleMapper.batchSelectPlayerActiveOrderStats(List.of(1L, 2L)))
                .thenReturn(List.of(stats(2L, 1, 0)));

        service.enrichBatch(List.of(available, full));

        assertEquals("AVAILABLE", available.getWorkStatus());
        assertEquals(0, available.getActiveOrders());
        assertEquals(0, available.getPendingAssignedOrders());
        assertEquals("FULL", full.getWorkStatus());
        assertEquals(1, full.getActiveOrders());
        assertEquals(1, full.getMaxConcurrent());
    }

    @Test
    void queryPageFiltersBeforeSlicing() {
        Player firstAvailable = player(1L, 1);
        Player offline = player(2L, 0);
        Player secondAvailable = player(3L, 1);
        when(playerService.list(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(firstAvailable, offline, secondAvailable));
        when(crossModuleMapper.batchSelectPlayerActiveOrderStats(List.of(1L, 2L, 3L)))
                .thenReturn(List.of());
        PageQuery query = new PageQuery();
        query.setPageNum(2);
        query.setPageSize(1);

        Page<Player> result = service.queryPage(
                query,
                new LambdaQueryWrapper<>(),
                "AVAILABLE");

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(3L, result.getRecords().get(0).getId());
        assertEquals("AVAILABLE", result.getRecords().get(0).getWorkStatus());
    }

    private Player player(Long id, int isOnline) {
        Player player = new Player();
        player.setId(id);
        player.setStatus("ACTIVE");
        player.setIsOnline(isOnline);
        return player;
    }

    private PlayerActiveOrderStats stats(
            Long playerId,
            int activeOrders,
            int pendingAssignedOrders) {
        PlayerActiveOrderStats stats = new PlayerActiveOrderStats();
        stats.setPlayerId(playerId);
        stats.setActiveOrders(activeOrders);
        stats.setPendingAssignedOrders(pendingAssignedOrders);
        return stats;
    }
}
