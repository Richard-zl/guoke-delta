package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.player.dto.PlayerShowcaseCardVO;
import com.delta.player.dto.PlayerShowcaseDetailVO;
import com.delta.player.dto.PlayerShowcaseSelfVO;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerShowcase;
import com.delta.player.mapper.PlayerShowcaseMapper;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerShowcaseRules;
import com.delta.player.service.PlayerShowcaseService;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlayerShowcaseServiceImpl extends ServiceImpl<PlayerShowcaseMapper, PlayerShowcase>
        implements PlayerShowcaseService {
    private final PlayerService playerService;
    private final CrossModuleMapper crossModuleMapper;
    private final SysConfigService sysConfigService;

    @Override
    public Page<PlayerShowcaseCardVO> pageActive(int pageNum, int pageSize) {
        Page<PlayerShowcase> page = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PlayerShowcase>()
                        .eq(PlayerShowcase::getStatus, 1)
                        .orderByAsc(PlayerShowcase::getSortOrder)
                        .orderByDesc(PlayerShowcase::getId));
        Page<PlayerShowcaseCardVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream()
                .map(this::toCard)
                .filter(v -> v != null)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public PlayerShowcaseDetailVO getPublicDetail(Long playerId) {
        PlayerShowcase card = getOne(new LambdaQueryWrapper<PlayerShowcase>()
                .eq(PlayerShowcase::getPlayerId, playerId)
                .eq(PlayerShowcase::getStatus, 1));
        Player player = playerService.getById(playerId);
        if (card == null || player == null || !"ACTIVE".equals(player.getStatus())) {
            throw new BusinessException("该打手暂未展示");
        }
        return toDetail(card, player);
    }

    @Override
    public PlayerShowcaseSelfVO getSelf(Long playerId) {
        Player player = requirePlayer(playerId);
        PlayerShowcase card = getOne(new LambdaQueryWrapper<PlayerShowcase>()
                .eq(PlayerShowcase::getPlayerId, playerId));
        PlayerShowcaseSelfVO vo = new PlayerShowcaseSelfVO();
        vo.setOnWall(card != null && Integer.valueOf(1).equals(card.getStatus())
                && "ACTIVE".equals(player.getStatus()));
        vo.setIntroVoiceUrl(empty(player.getIntroVoiceUrl()));
        vo.setIntroVoiceSeconds(player.getIntroVoiceSeconds() == null ? 0 : player.getIntroVoiceSeconds());
        vo.setHighlightImages(PlayerShowcaseRules.csvList(player.getHighlightImages()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveVoice(Long playerId, String url, Integer seconds) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("请先上传语音");
        }
        String reason = PlayerShowcaseRules.voiceBlockReason(seconds);
        if (reason != null) {
            throw new BusinessException(reason);
        }
        Player patch = new Player();
        patch.setId(playerId);
        patch.setIntroVoiceUrl(url.trim());
        patch.setIntroVoiceSeconds(seconds);
        playerService.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGallery(Long playerId, List<String> urls) {
        List<String> list = urls == null ? List.of() : urls;
        if (list.size() > PlayerShowcaseRules.MAX_GALLERY) {
            throw new BusinessException("高光图最多9张");
        }
        String csv = PlayerShowcaseRules.toCsv(list);
        Player patch = new Player();
        patch.setId(playerId);
        patch.setHighlightImages(csv);
        playerService.updateById(patch);

        PlayerShowcase card = getOne(new LambdaQueryWrapper<PlayerShowcase>()
                .eq(PlayerShowcase::getPlayerId, playerId));
        if (card != null) {
            card.setSelectedImages(PlayerShowcaseRules.filterSelectedImages(card.getSelectedImages(), csv));
            updateById(card);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCard(PlayerShowcase card) {
        if (card.getPlayerId() == null) {
            throw new BusinessException("请选择打手");
        }
        Player player = requirePlayer(card.getPlayerId());
        if (card.getBio() != null && card.getBio().length() > PlayerShowcaseRules.MAX_BIO) {
            throw new BusinessException("简介最多200字");
        }
        String metricReason = PlayerShowcaseRules.metricBlockReason(
                card.getDisplayRating(),
                card.getDisplayCompletedOrders(),
                card.getDisplayCompleteRate());
        if (metricReason != null) {
            throw new BusinessException(metricReason);
        }
        card.setSelectedImages(PlayerShowcaseRules.filterSelectedImages(
                card.getSelectedImages(), player.getHighlightImages()));
        if (Integer.valueOf(1).equals(card.getStatus())) {
            assertCanPublish(player, card);
        }
        if (card.getId() == null) {
            long exists = count(new LambdaQueryWrapper<PlayerShowcase>()
                    .eq(PlayerShowcase::getPlayerId, card.getPlayerId()));
            if (exists > 0) {
                throw new BusinessException("该打手已有风采卡");
            }
            save(card);
            return;
        }
        PlayerShowcase db = getById(card.getId());
        if (db == null) {
            throw new BusinessException("风采卡不存在");
        }
        if (!db.getPlayerId().equals(card.getPlayerId())) {
            throw new BusinessException("不可改绑打手");
        }
        updateById(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        PlayerShowcase card = getById(id);
        if (card == null) {
            throw new BusinessException("风采卡不存在");
        }
        if (Integer.valueOf(1).equals(status)) {
            assertCanPublish(requirePlayer(card.getPlayerId()), card);
        }
        card.setStatus(status == null ? 0 : status);
        updateById(card);
    }

    @Override
    public void unpublishByPlayerId(Long playerId) {
        if (playerId == null) {
            return;
        }
        update(new LambdaUpdateWrapper<PlayerShowcase>()
                .eq(PlayerShowcase::getPlayerId, playerId)
                .set(PlayerShowcase::getStatus, 0));
    }

    @Override
    public Set<Long> findOnWallPlayerIds(List<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return Set.of();
        }
        return list(new LambdaQueryWrapper<PlayerShowcase>()
                .in(PlayerShowcase::getPlayerId, playerIds)
                .eq(PlayerShowcase::getStatus, 1))
                .stream()
                .map(PlayerShowcase::getPlayerId)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public void fillOnWall(List<Player> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        Set<Long> ids = findOnWallPlayerIds(players.stream().map(Player::getId).toList());
        for (Player p : players) {
            p.setOnWall(p.getId() != null && ids.contains(p.getId()));
        }
    }

    private void assertCanPublish(Player player, PlayerShowcase card) {
        String reason = PlayerShowcaseRules.publishBlockReason(
                player.getStatus(), player.getIntroVoiceUrl(), card.getTagline());
        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private PlayerShowcaseCardVO toCard(PlayerShowcase card) {
        Player player = playerService.getById(card.getPlayerId());
        if (player == null || !"ACTIVE".equals(player.getStatus())) {
            if (player != null) {
                unpublishByPlayerId(player.getId());
            }
            return null;
        }
        return fillCard(new PlayerShowcaseCardVO(), card, player);
    }

    private PlayerShowcaseDetailVO toDetail(PlayerShowcase card, Player player) {
        PlayerShowcaseDetailVO vo = fillCard(new PlayerShowcaseDetailVO(), card, player);
        vo.setBio(empty(card.getBio()));
        vo.setIntroVoiceUrl(empty(player.getIntroVoiceUrl()));
        vo.setIntroVoiceSeconds(player.getIntroVoiceSeconds() == null ? 0 : player.getIntroVoiceSeconds());
        String selected = PlayerShowcaseRules.filterSelectedImages(
                card.getSelectedImages(), player.getHighlightImages());
        vo.setHighlightImages(PlayerShowcaseRules.csvList(selected));
        return vo;
    }

    private <T extends PlayerShowcaseCardVO> T fillCard(T vo, PlayerShowcase card, Player player) {
        int max = Integer.parseInt(sysConfigService.getConfigValue("order.max_active_per_player", "1"));
        int active = crossModuleMapper.selectPlayerActiveOrders(player.getId());
        int completed = crossModuleMapper.selectPlayerCompletedOrders(player.getId());
        vo.setPlayerId(player.getId());
        vo.setNickname(player.getNickname());
        vo.setAvatar(player.getAvatar());
        String cover = card.getCoverUrl();
        vo.setCoverUrl(cover == null || cover.isBlank() ? player.getAvatar() : cover);
        vo.setTagline(empty(card.getTagline()));
        vo.setAvgRating(card.getDisplayRating() != null
                ? card.getDisplayRating() : player.getAvgRating());
        vo.setCompletedOrders(card.getDisplayCompletedOrders() != null
                ? card.getDisplayCompletedOrders() : completed);
        vo.setCompleteRate(card.getDisplayCompleteRate() != null
                ? card.getDisplayCompleteRate() : player.getCompleteRate());
        vo.setSkillTags(PlayerShowcaseRules.csvList(player.getSkillTags()));
        vo.setIsOnline(player.getIsOnline());
        vo.setActiveOrders(active);
        vo.setMaxConcurrent(max);
        vo.setCanDesignate(PlayerShowcaseRules.canDesignate(
                player.getStatus(), player.getIsOnline(), active, max));
        vo.setDesignateBlockReason(PlayerShowcaseRules.designateBlockReason(
                player.getStatus(), player.getIsOnline(), active, max));
        return vo;
    }

    private Player requirePlayer(Long playerId) {
        Player player = playerService.getById(playerId);
        if (player == null) {
            throw new BusinessException("打手不存在");
        }
        return player;
    }

    private static String empty(String s) {
        return s == null ? "" : s;
    }
}
