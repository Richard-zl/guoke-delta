package com.delta.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_variant")
public class ProductVariant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String name;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer sortOrder;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
}
