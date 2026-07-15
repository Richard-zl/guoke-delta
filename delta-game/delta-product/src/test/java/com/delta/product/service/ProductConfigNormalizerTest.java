package com.delta.product.service;

import com.delta.common.exception.BusinessException;
import com.delta.product.entity.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductConfigNormalizerTest {

    @Test
    void normalizeQuantity_限购与数量互斥() {
        Product product = new Product();
        product.setQuantityEnabled(1);
        product.setPerUserLimitType(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ProductConfigNormalizer.normalizeQuantity(product));
        assertEquals("限购商品不可开启数量选择", ex.getMessage());
    }

    @Test
    void normalizeQuantity_开启时填充默认值() {
        Product product = new Product();
        product.setQuantityEnabled(1);
        product.setPerUserLimitType(0);

        ProductConfigNormalizer.normalizeQuantity(product);

        assertEquals("份", product.getUnitLabel());
        assertEquals(24, product.getMaxQuantity());
    }

    @Test
    void normalizeQuantity_关闭时清空字段() {
        Product product = new Product();
        product.setQuantityEnabled(0);
        product.setUnitLabel("小时");
        product.setMaxQuantity(10);

        ProductConfigNormalizer.normalizeQuantity(product);

        assertEquals(0, product.getQuantityEnabled());
        assertNull(product.getUnitLabel());
        assertNull(product.getMaxQuantity());
    }
}
