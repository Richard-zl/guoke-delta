package com.delta.order.service;

import com.delta.common.exception.BusinessException;
import com.delta.product.entity.Product;
import com.delta.product.entity.ProductVariant;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 下单计价：解析规格单价、数量与小计。
 */
public final class OrderPriceResolver {

    private OrderPriceResolver() {
    }

    @Getter
    public static class PriceBreakdown {
        private final BigDecimal unitPrice;
        private final int quantity;
        private final BigDecimal subtotal;
        private final Long variantId;
        private final String variantName;

        public PriceBreakdown(BigDecimal unitPrice, int quantity, BigDecimal subtotal,
                              Long variantId, String variantName) {
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.subtotal = subtotal;
            this.variantId = variantId;
            this.variantName = variantName;
        }
    }

    public static PriceBreakdown resolve(Product product, List<ProductVariant> variants,
                                         Long requestVariantId, Integer requestQuantity) {
        BigDecimal unitPrice;
        Long variantId = null;
        String variantName = null;

        boolean hasVariants = variants != null && !variants.isEmpty();
        if (hasVariants) {
            if (requestVariantId == null) {
                throw new BusinessException("请选择规格");
            }
            ProductVariant variant = variants.stream()
                    .filter(v -> requestVariantId.equals(v.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("规格不可用"));
            unitPrice = variant.getPrice();
            variantId = variant.getId();
            variantName = variant.getName();
        } else {
            unitPrice = product.getPrice();
            if (unitPrice == null) {
                throw new BusinessException("商品未设置价格");
            }
        }
        unitPrice = normalizeMoney(unitPrice);

        int quantity = resolveQuantity(product, requestQuantity);
        BigDecimal subtotal = normalizeMoney(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        return new PriceBreakdown(unitPrice, quantity, subtotal, variantId, variantName);
    }

    private static int resolveQuantity(Product product, Integer requestQuantity) {
        if (!Integer.valueOf(1).equals(product.getQuantityEnabled())) {
            return 1;
        }
        int max = product.getMaxQuantity() != null && product.getMaxQuantity() > 0
                ? product.getMaxQuantity() : 24;
        int quantity = requestQuantity != null ? requestQuantity : 1;
        if (quantity < 1 || quantity > max) {
            throw new BusinessException("购买数量超出限制");
        }
        return quantity;
    }

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
