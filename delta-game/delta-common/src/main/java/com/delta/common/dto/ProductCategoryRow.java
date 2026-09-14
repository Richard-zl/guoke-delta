package com.delta.common.dto;

import lombok.Data;

/** 商品所属分类（含父级），用于订单派单文案。 */
@Data
public class ProductCategoryRow {
    private Long productId;
    private Long categoryId;
    private String categoryName;
    private Long parentId;
}
