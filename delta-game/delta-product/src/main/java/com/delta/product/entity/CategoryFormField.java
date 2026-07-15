package com.delta.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("category_form_field")
public class CategoryFormField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String fieldKey;
    private String fieldLabel;
    private String fieldType;
    private String options;
    private String placeholder;
    private Integer required;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
