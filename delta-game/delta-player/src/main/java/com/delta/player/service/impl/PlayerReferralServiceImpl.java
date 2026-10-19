package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.exception.BusinessException;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerReferral;
import com.delta.player.mapper.PlayerReferralMapper;
import com.delta.player.service.PlayerReferralRules;
import com.delta.player.service.PlayerReferralService;
import com.delta.player.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerReferralServiceImpl extends ServiceImpl<PlayerReferralMapper, PlayerReferral>
        implements PlayerReferralService {

    private final PlayerService playerService;

    @Override
    public List<PlayerReferral> listActive(Long playerId) {
        return list(new LambdaQueryWrapper<PlayerReferral>()
                .eq(PlayerReferral::getPlayerId, playerId)
                .eq(PlayerReferral::getStatus, PlayerReferralRules.STATUS_ACTIVE)
                .orderByDesc(PlayerReferral::getBoundAt)
                .orderByDesc(PlayerReferral::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlayerReferral bind(Long playerId, Long userId, BigDecimal rate) {
        Player player = playerService.getById(playerId);
        if (player == null) {
            throw new BusinessException("打手不存在");
        }
        String block = PlayerReferralRules.bindBlockReason(player.getStatus());
        if (block != null) {
            throw new BusinessException(block);
        }
        if (userId == null) {
            throw new BusinessException("请选择客户");
        }
        PlayerReferral existing = findActiveByUser(userId);
        if (existing != null) {
            throw boundByOther(existing);
        }
        PlayerReferral row = new PlayerReferral();
        row.setPlayerId(playerId);
        row.setUserId(userId);
        row.setRate(rate);
        row.setStatus(PlayerReferralRules.STATUS_ACTIVE);
        row.setActiveKey(0L);
        row.setBoundAt(LocalDateTime.now());
        try {
            save(row);
        } catch (DuplicateKeyException ex) {
            PlayerReferral winner = findActiveByUser(userId);
            throw winner == null ? new BusinessException("该客户已有介绍人，请先解绑") : boundByOther(winner);
        }
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRate(Long playerId, Long referralId, BigDecimal rate) {
        PlayerReferral row = requireOwnedActive(playerId, referralId);
        row.setRate(rate);
        updateById(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long playerId, Long referralId) {
        PlayerReferral row = requireOwnedActive(playerId, referralId);
        markUnbound(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unbindAll(Long playerId) {
        List<PlayerReferral> rows = listActive(playerId);
        for (PlayerReferral row : rows) {
            markUnbound(row);
        }
        return rows.size();
    }

    @Override
    public PlayerReferral activeForSettlement(Long userId) {
        if (userId == null) {
            return null;
        }
        PlayerReferral binding = findActiveByUser(userId);
        if (binding == null) {
            return null;
        }
        Player player = playerService.getById(binding.getPlayerId());
        // 冻结期间不记介绍费，绑定本身保留
        if (player == null || !"ACTIVE".equals(player.getStatus())) {
            return null;
        }
        return binding;
    }

    private PlayerReferral findActiveByUser(Long userId) {
        return getOne(new LambdaQueryWrapper<PlayerReferral>()
                .eq(PlayerReferral::getUserId, userId)
                .eq(PlayerReferral::getStatus, PlayerReferralRules.STATUS_ACTIVE)
                .last("LIMIT 1"));
    }

    private PlayerReferral requireOwnedActive(Long playerId, Long referralId) {
        PlayerReferral row = getById(referralId);
        if (row == null || !playerId.equals(row.getPlayerId())
                || !PlayerReferralRules.STATUS_ACTIVE.equals(row.getStatus())) {
            throw new BusinessException("转介绍客户不存在");
        }
        return row;
    }

    /** 解绑后把 active_key 改成行 id，空出 (user_id, 0) 以便以后重新绑定。 */
    private void markUnbound(PlayerReferral row) {
        boolean updated = update(new LambdaUpdateWrapper<PlayerReferral>()
                .eq(PlayerReferral::getId, row.getId())
                .eq(PlayerReferral::getStatus, PlayerReferralRules.STATUS_ACTIVE)
                .set(PlayerReferral::getStatus, PlayerReferralRules.STATUS_UNBOUND)
                .set(PlayerReferral::getActiveKey, row.getId())
                .set(PlayerReferral::getUnboundAt, LocalDateTime.now()));
        if (!updated) {
            throw new BusinessException("转介绍客户不存在");
        }
    }

    private BusinessException boundByOther(PlayerReferral existing) {
        Player owner = playerService.getById(existing.getPlayerId());
        String name = owner == null || owner.getNickname() == null ? "未知打手" : owner.getNickname();
        return new BusinessException("该客户已绑定介绍人：" + name + "（ID:" + existing.getPlayerId() + "），请先解绑");
    }
}
