package com.delta.order.service;

import com.delta.common.exception.BusinessException;
import com.delta.product.entity.Product;
import com.delta.product.entity.ProductVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderPriceResolverTest {

    @Test
    void resolve_无规格无数量_单价等于商品价格() {
        Product product = baseProduct();
        product.setPrice(new BigDecimal("50.00"));

        OrderPriceResolver.PriceBreakdown result = OrderPriceResolver.resolve(
                product, List.of(), null, null);

        assertEquals(new BigDecimal("50.00"), result.getUnitPrice());
        assertEquals(1, result.getQuantity());
        assertEquals(new BigDecimal("50.00"), result.getSubtotal());
        assertNull(result.getVariantId());
    }

    @Test
    void resolve_有规格_必须选择规格() {
        Product product = baseProduct();
        ProductVariant variant = variant(1L, "王者局", "60.00");

        assertThrows(BusinessException.class, () ->
                OrderPriceResolver.resolve(product, List.of(variant), null, 1));
    }

    @Test
    void resolve_有规格_按规格价计价() {
        Product product = baseProduct();
        ProductVariant variant = variant(1L, "王者局", "60.00");

        OrderPriceResolver.PriceBreakdown result = OrderPriceResolver.resolve(
                product, List.of(variant), 1L, null);

        assertEquals(new BigDecimal("60.00"), result.getUnitPrice());
        assertEquals("王者局", result.getVariantName());
        assertEquals(1L, result.getVariantId());
    }

    @Test
    void resolve_开启数量_小计等于单价乘数量() {
        Product product = baseProduct();
        product.setPrice(new BigDecimal("30.00"));
        product.setQuantityEnabled(1);
        product.setMaxQuantity(10);

        OrderPriceResolver.PriceBreakdown result = OrderPriceResolver.resolve(
                product, List.of(), null, 3);

        assertEquals(new BigDecimal("30.00"), result.getUnitPrice());
        assertEquals(3, result.getQuantity());
        assertEquals(new BigDecimal("90.00"), result.getSubtotal());
    }

    @Test
    void resolve_规格加数量_总价为规格价乘数量() {
        Product product = baseProduct();
        product.setQuantityEnabled(1);
        product.setMaxQuantity(5);
        ProductVariant variant = variant(2L, "黄金局", "35.00");

        OrderPriceResolver.PriceBreakdown result = OrderPriceResolver.resolve(
                product, List.of(variant), 2L, 2);

        assertEquals(new BigDecimal("35.00"), result.getUnitPrice());
        assertEquals(2, result.getQuantity());
        assertEquals(new BigDecimal("70.00"), result.getSubtotal());
    }

    @Test
    void resolve_数量越界_抛异常() {
        Product product = baseProduct();
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantityEnabled(1);
        product.setMaxQuantity(3);

        assertThrows(BusinessException.class, () ->
                OrderPriceResolver.resolve(product, List.of(), null, 4));
    }

    @Test
    void resolve_未开数量_忽略请求数量() {
        Product product = baseProduct();
        product.setPrice(new BigDecimal("10.00"));

        OrderPriceResolver.PriceBreakdown result = OrderPriceResolver.resolve(
                product, List.of(), null, 5);

        assertEquals(1, result.getQuantity());
        assertEquals(new BigDecimal("10.00"), result.getSubtotal());
    }

    private Product baseProduct() {
        Product product = new Product();
        product.setId(100L);
        product.setQuantityEnabled(0);
        return product;
    }

    private ProductVariant variant(Long id, String name, String price) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProductId(100L);
        variant.setName(name);
        variant.setPrice(new BigDecimal(price));
        return variant;
    }
}
