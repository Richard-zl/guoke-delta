package com.delta.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("coupon")
public class Coupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private BigDecimal discountRate;
    private BigDecimal cashAmount;
    private BigDecimal minAmount;
    private Integer validDays;
    private Integer status;
    private LocalDateTime createdAt;
}