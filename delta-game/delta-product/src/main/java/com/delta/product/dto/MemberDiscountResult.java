package com.delta.product.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员等级折扣计算结果
 */
@Data
@Builder
public class MemberDiscountResult {
    /** 原价 */
    private BigDecimal originalAmount;
    /** 等级折后金额 */
    private BigDecimal amountAfterMemberDiscount;
    /** 折扣率（1.00 表示未打折） */
    private BigDecimal discountRate;
    /** 折扣减免金额 */
    private BigDecimal discountAmount;
    /** 等级名称（用于明细展示） */
    private String levelName;
    /** 等级代码 */
    private String levelCode;
    /** 是否实际应用了会员折扣 */
    private boolean applied;
    /** 未应用原因（排除分类 / 无折扣等级） */
    private String skipReason;
}
