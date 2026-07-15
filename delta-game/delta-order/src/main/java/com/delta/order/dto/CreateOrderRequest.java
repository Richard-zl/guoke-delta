package com.delta.order.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    private Long productId;
    private String specInfo;
    /** 选中的规格选项ID */
    private Long variantId;
    /** 购买数量 */
    private Integer quantity;
    private BigDecimal amount;
    private String gameAccount;
    private String contact;
    private String remark;
    private Long designatedPlayerId;
    private java.util.Map<String, String> extraFields;

    /**
     * 优惠券ID
     */
    private Long couponId;
}