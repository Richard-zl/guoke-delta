package com.delta.player.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.player.entity.Player;

import java.util.List;

/**
 * 统一填充并查询打手工作状态。
 */
public interface PlayerWorkStatusService {

    void enrichOne(Player player);

    void enrichBatch(List<Player> players);

    Page<Player> queryPage(
            PageQuery query,
            LambdaQueryWrapper<Player> wrapper,
            String workStatus);

    int getMaxConcurrent();
}
