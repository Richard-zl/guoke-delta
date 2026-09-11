package com.delta.player.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.player.dto.PlayerShowcaseCardVO;
import com.delta.player.dto.PlayerShowcaseDetailVO;
import com.delta.player.dto.PlayerShowcaseSelfVO;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerShowcase;

import java.util.List;
import java.util.Set;

public interface PlayerShowcaseService extends IService<PlayerShowcase> {
    Page<PlayerShowcaseCardVO> pageActive(int pageNum, int pageSize);

    PlayerShowcaseDetailVO getPublicDetail(Long playerId);

    PlayerShowcaseSelfVO getSelf(Long playerId);

    void saveVoice(Long playerId, String url, Integer seconds);

    void saveGallery(Long playerId, List<String> urls);

    void saveCard(PlayerShowcase card);

    void updateStatus(Long id, Integer status);

    void unpublishByPlayerId(Long playerId);

    Set<Long> findOnWallPlayerIds(List<Long> playerIds);

    void fillOnWall(List<Player> players);
}
