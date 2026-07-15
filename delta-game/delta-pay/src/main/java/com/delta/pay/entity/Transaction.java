package com.delta.pay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("transaction")
public class Transaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String userType;
    private Long userId;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private Long relatedOrderId;
    private Long relatedPaymentId;
    private Long relatedWithdrawId;
    private String remark;
    private LocalDateTime createdAt;
}
