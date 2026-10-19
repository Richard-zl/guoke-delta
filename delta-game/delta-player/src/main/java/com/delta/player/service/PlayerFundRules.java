package com.delta.player.service;

import com.delta.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 打手人工调账、离职和删除的校验规则。
 */
public final class PlayerFundRules {

    public static final String DIRECTION_ADD = "ADD";
    public static final String DIRECTION_DEDUCT = "DEDUCT";
    public static final String STATUS_RESIGNED = "RESIGNED";

    /** 删除前仍视为未完结的订单状态。 */
    public static final List<String> OPEN_ORDER_STATUSES = List.of(
            "ASSIGNED", "ACCEPTED", "WAITING_TEAMMATE", "IN_PROGRESS",
            "COMPLETED", "DISPUTED", "REFUNDING");

    private static final int REMARK_MAX = 200;

    private PlayerFundRules() {
    }

    /** 把补款/扣款方向转成带符号金额，正数入账，负数出账。 */
    public static BigDecimal signedAmount(String direction, BigDecimal amount) {
        if (!DIRECTION_ADD.equals(direction) && !DIRECTION_DEDUCT.equals(direction)) {
            throw new BusinessException("请选择补款或扣款");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("调整金额必须大于0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessException("调整金额最多保留两位小数");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        return DIRECTION_ADD.equals(direction) ? normalized : normalized.negate();
    }

    public static void assertBalanceEnough(BigDecimal balanceAfter) {
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("扣款后余额不能为负数");
        }
    }

    /** 原因必填，超长直接拒绝，避免资金流水备注被截断后无法对账。 */
    public static String normalizeRemark(String remark) {
        String text = remark == null ? "" : remark.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请填写调整原因");
        }
        if (text.length() > REMARK_MAX) {
            throw new BusinessException("调整原因不能超过200字");
        }
        return text;
    }

    /** 返回不能离职的原因；可以离职时返回 null。 */
    public static String resignBlockReason(String status) {
        if (STATUS_RESIGNED.equals(status)) {
            return "该打手已经离职";
        }
        if ("ACTIVE".equals(status) || "FROZEN".equals(status)) {
            return null;
        }
        return "只有正常或已冻结的打手可以离职";
    }

    /**
     * 返回不能删除的原因；可以删除时返回 null。
     * 未结清的可提现、待入账、冻结金额都要先处理完。
     */
    public static String deleteBlockReason(String status,
                                            BigDecimal balance,
                                            BigDecimal pendingBalance,
                                            BigDecimal frozenAmount,
                                            int openOrders,
                                            int pendingWithdraws) {
        if (!STATUS_RESIGNED.equals(status)) {
            return "只能删除已离职的打手";
        }
        if (openOrders > 0) {
            return "还有未完结订单，不能删除";
        }
        if (pendingWithdraws > 0) {
            return "还有待审核提现，不能删除";
        }
        if (nonzero(balance)) {
            return "可提现余额不为0，不能删除";
        }
        if (nonzero(pendingBalance)) {
            return "还有待入账金额，不能删除";
        }
        if (nonzero(frozenAmount)) {
            return "还有冻结金额，不能删除";
        }
        return null;
    }

    private static boolean nonzero(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) != 0;
    }
}
