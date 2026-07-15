package com.delta.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("price_rule")
public class PriceRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String specCombination;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private LocalDateTime createdAt;
}
