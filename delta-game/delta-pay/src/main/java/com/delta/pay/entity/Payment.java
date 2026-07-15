package com.delta.pay.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.delta.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment")
public class Payment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private Long orderId;
    private Long userId;
    /** 业务类型: ORDER-订单支付 PLAYER_DEPOSIT-打手押金 */
    private String bizType;
    private BigDecimal amount;
    private String payMethod;
    /** 支付渠道: MINIAPP-小程序 JSAPI / MP_H5-服务号 H5 JSAPI */
    private String payChannel;
    private String status;
    private String wxTransactionId;
    private String wxPrepayId;
    private BigDecimal refundAmount;
    private String refundNo;
    private String refundReason;
    private LocalDateTime refundTime;
    private LocalDateTime paidAt;
}
