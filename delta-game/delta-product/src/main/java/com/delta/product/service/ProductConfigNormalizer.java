package com.delta.product.service;

import com.delta.common.exception.BusinessException;
import com.delta.product.entity.Product;
import com.delta.product.enums.ProductLimitTypeEnum;

/** 商品数量与限购配置归一化 */
public final class ProductConfigNormalizer {

    private ProductConfigNormalizer() {
    }

    public static void normalizeQuantity(Product product) {
        if (product == null) {
            return;
        }
        boolean quantityEnabled = Integer.valueOf(1).equals(product.getQuantityEnabled());
        if (!quantityEnabled) {
            product.setQuantityEnabled(0);
            product.setUnitLabel(null);
            product.setMaxQuantity(null);
            return;
        }
        ProductLimitTypeEnum limitType = ProductLimitTypeEnum.resolve(
                product.getPerUserLimitType(),
                product.getPerUserLimitEnabled(),
                product.getPerUserLimitCount());
        if (limitType.isLimited()) {
            throw new BusinessException("限购商品不可开启数量选择");
        }
        if (product.getMaxQuantity() == null || product.getMaxQuantity() < 1) {
            product.setMaxQuantity(24);
        }
        if (product.getUnitLabel() == null || product.getUnitLabel().isBlank()) {
            product.setUnitLabel("份");
        } else {
            product.setUnitLabel(product.getUnitLabel().trim());
        }
    }
}
