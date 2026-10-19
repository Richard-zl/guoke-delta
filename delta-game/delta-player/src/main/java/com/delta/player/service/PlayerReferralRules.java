package com.delta.player.service;

import com.delta.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 转介绍比例、结算和退款扣回的纯计算。
 * 介绍费从俱乐部抽成中出，不减少接单打手收入。
 */
public final class PlayerReferralRules {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_UNBOUND = "UNBOUND";
    public static final String TX_REFERRAL = "REFERRAL";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private PlayerReferralRules() {
    }

    /** 把后台填写的百分比换成库存小数。5.12 表示 5.12%，存成 0.0512。 */
    public static BigDecimal normalizeRate(BigDecimal percent) {
        if (percent == null || percent.compareTo(ZERO) <= 0 || percent.compareTo(HUNDRED) > 0) {
            throw new BusinessException("介绍比例需大于0且不超过100");
        }
        if (percent.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("介绍比例最多保留两位小数");
        }
        return percent.divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }

    /** 返回不能新增绑定的原因；可以新增时返回 null。 */
    public static String bindBlockReason(String playerStatus) {
        if ("ACTIVE".equals(playerStatus) || "FROZEN".equals(playerStatus)) {
            return null;
        }
        if ("RESIGNED".equals(playerStatus)) {
            return "离职打手不能新增转介绍客户";
        }
        return "只有正常或已冻结的打手可以新增转介绍客户";
    }

    /**
     * 按订单金额、抽成比例、介绍比例算出三方金额。
     * 抽成和介绍费都四舍五入到分，介绍费不超过抽成。
     */
    public static Split settle(BigDecimal orderAmount, BigDecimal commissionRate, BigDecimal referralRate) {
        BigDecimal amount = orderAmount == null ? ZERO : orderAmount;
        BigDecimal commission = amount.multiply(rateOrZero(commissionRate)).setScale(2, RoundingMode.HALF_UP);
        if (commission.signum() < 0) {
            commission = ZERO;
        }
        BigDecimal playerPool = amount.subtract(commission);
        BigDecimal fee = referralFee(amount, referralRate, commission);
        return new Split(playerPool, fee, commission.subtract(fee));
    }

    /** 介绍费 = min(订单金额 × 介绍比例, 俱乐部抽成)，四舍五入到分。 */
    public static BigDecimal referralFee(BigDecimal orderAmount, BigDecimal referralRate, BigDecimal commission) {
        if (orderAmount == null || referralRate == null || commission == null) {
            return ZERO;
        }
        if (orderAmount.signum() <= 0 || referralRate.signum() <= 0 || commission.signum() <= 0) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal raw = orderAmount.multiply(referralRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cap = commission.setScale(2, RoundingMode.HALF_UP);
        return raw.min(cap);
    }

    /** 这一单还没扣回的介绍费。 */
    public static BigDecimal remaining(BigDecimal referralAmount, BigDecimal alreadyClawed) {
        BigDecimal total = referralAmount == null ? ZERO : referralAmount;
        BigDecimal clawed = alreadyClawed == null ? ZERO : alreadyClawed;
        BigDecimal left = total.subtract(clawed);
        return left.signum() > 0 ? left : ZERO;
    }

    /**
     * 本次退款应扣回的介绍费。
     * 按退款占原订单金额的比例计算，累计不超过该单介绍费。
     */
    public static BigDecimal clawbackDue(BigDecimal orderAmount,
                                          BigDecimal referralAmount,
                                          BigDecimal alreadyClawed,
                                          BigDecimal refundAmount) {
        if (orderAmount == null || orderAmount.signum() <= 0 || refundAmount == null || refundAmount.signum() <= 0) {
            return ZERO;
        }
        BigDecimal left = remaining(referralAmount, alreadyClawed);
        if (left.signum() <= 0 || referralAmount == null) {
            return ZERO;
        }
        BigDecimal due = referralAmount.multiply(refundAmount).divide(orderAmount, 2, RoundingMode.HALF_UP);
        if (due.compareTo(left) > 0) {
            return left;
        }
        return due.signum() > 0 ? due : ZERO;
    }

    private static BigDecimal rateOrZero(BigDecimal rate) {
        return rate == null ? ZERO : rate;
    }

    /** 接单打手池、介绍费、俱乐部实得。 */
    public record Split(BigDecimal playerPool, BigDecimal referralFee, BigDecimal clubNet) {
    }
}
