package com.delta.player.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.player.entity.PlayerReferral;
import com.delta.player.service.PlayerReferralRules;
import com.delta.player.service.PlayerReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打手端转介绍客户，只读。
 */
@RestController
@RequestMapping("/player/referral")
@RequiredArgsConstructor
public class PlayerReferralController {

    private final PlayerReferralService playerReferralService;
    private final OrderService orderService;
    private final CrossModuleMapper crossModuleMapper;

    @GetMapping("/customers")
    public R<List<Map<String, Object>>> customers() {
        Long playerId = SecurityUtils.getUserId();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlayerReferral referral : playerReferralService.listActive(playerId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", referral.getUserId());
            row.put("nickname", crossModuleMapper.selectUserNickname(referral.getUserId()));
            row.put("avatar", crossModuleMapper.selectUserAvatar(referral.getUserId()));
            row.put("phone", crossModuleMapper.selectUserPhone(referral.getUserId()));
            row.put("ratePercent", percent(referral.getRate()));
            row.put("boundAt", referral.getBoundAt());
            rows.add(row);
        }
        return R.ok(rows);
    }

    @GetMapping("/customers/{userId}/fees")
    public R<Map<String, Object>> fees(@PathVariable Long userId) {
        Long playerId = SecurityUtils.getUserId();
        PlayerReferral referral = playerReferralService.listActive(playerId).stream()
                .filter(item -> userId.equals(item.getUserId()))
                .findFirst()
                .orElse(null);
        if (referral == null) {
            throw new BusinessException("没有该转介绍客户");
        }
        List<Order> orders = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getReferralPlayerId, playerId)
                .isNotNull(Order::getReferralAmount)
                .orderByDesc(Order::getConfirmTime)
                .orderByDesc(Order::getId));
        List<Map<String, Object>> fees = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> fee = new LinkedHashMap<>();
            fee.put("orderId", order.getId());
            fee.put("orderNo", order.getOrderNo());
            fee.put("amount", PlayerReferralRules.remaining(order.getReferralAmount(), order.getReferralClawedAmount()));
            fee.put("time", order.getConfirmTime());
            fee.put("settled", order.getSettled() != null && order.getSettled() == 1);
            fees.add(fee);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("nickname", crossModuleMapper.selectUserNickname(userId));
        data.put("avatar", crossModuleMapper.selectUserAvatar(userId));
        data.put("phone", crossModuleMapper.selectUserPhone(userId));
        data.put("ratePercent", percent(referral.getRate()));
        data.put("fees", fees);
        return R.ok(data);
    }

    private String percent(BigDecimal rate) {
        if (rate == null) {
            return "0";
        }
        return rate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString();
    }
}
