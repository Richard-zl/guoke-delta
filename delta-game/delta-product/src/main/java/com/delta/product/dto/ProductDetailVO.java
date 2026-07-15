package com.delta.product.dto;

import com.delta.product.entity.Product;
import com.delta.product.entity.ProductSpec;
import com.delta.product.entity.PriceRule;
import lombok.Data;
import java.util.List;

@Data
public class ProductDetailVO {
    private Product product;
    private List<ProductSpec> specs;
    private List<PriceRule> priceRules;
    /** 最低价 */
    private java.math.BigDecimal minPrice;
}
