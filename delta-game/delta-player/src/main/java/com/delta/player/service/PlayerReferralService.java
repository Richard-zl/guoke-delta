package com.delta.player.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.player.entity.PlayerReferral;

import java.math.BigDecimal;
import java.util.List;

public interface PlayerReferralService extends IService<PlayerReferral> {

    List<PlayerReferral> listActive(Long playerId);

    /** 新增有效绑定。rate 已是库存小数。 */
    PlayerReferral bind(Long playerId, Long userId, BigDecimal rate);

    void updateRate(Long playerId, Long referralId, BigDecimal rate);

    void unbind(Long playerId, Long referralId);

    /** 离职时解绑全部有效客户，返回解绑条数。 */
    int unbindAll(Long playerId);

    /**
     * 结算用：客户当前有效绑定，且介绍人状态为正常。
     * 没有绑定或介绍人已冻结时返回 null。
     */
    PlayerReferral activeForSettlement(Long userId);
}
