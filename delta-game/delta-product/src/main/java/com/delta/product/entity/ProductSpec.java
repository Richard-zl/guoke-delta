package com.delta.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_spec")
public class ProductSpec {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String specName;
    private String specValues;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
