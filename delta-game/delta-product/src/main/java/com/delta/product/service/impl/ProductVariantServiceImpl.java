package com.delta.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.exception.BusinessException;
import com.delta.product.entity.ProductVariant;
import com.delta.product.mapper.ProductVariantMapper;
import com.delta.product.service.ProductVariantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductVariantServiceImpl extends ServiceImpl<ProductVariantMapper, ProductVariant>
        implements ProductVariantService {

    @Override
    public List<ProductVariant> listActiveByProductId(Long productId) {
        if (productId == null) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<ProductVariant>()
                .eq(ProductVariant::getProductId, productId)
                .orderByAsc(ProductVariant::getSortOrder)
                .orderByAsc(ProductVariant::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceVariants(Long productId, List<ProductVariant> variants) {
        if (productId == null) {
            return;
        }
        remove(new LambdaQueryWrapper<ProductVariant>().eq(ProductVariant::getProductId, productId));
        if (variants == null || variants.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ProductVariant> toSave = new ArrayList<>();
        int sort = 0;
        for (ProductVariant variant : variants) {
            if (variant == null || !StringUtils.hasText(variant.getName())) {
                throw new BusinessException("规格名称不能为空");
            }
            if (variant.getPrice() == null || variant.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("规格价格不能为负数");
            }
            ProductVariant row = new ProductVariant();
            row.setProductId(productId);
            row.setName(variant.getName().trim());
            row.setPrice(variant.getPrice());
            row.setOriginalPrice(variant.getOriginalPrice());
            row.setSortOrder(variant.getSortOrder() != null ? variant.getSortOrder() : sort++);
            row.setCreatedAt(now);
            toSave.add(row);
        }
        saveBatch(toSave);
    }

    @Override
    public ProductVariant requireActiveVariant(Long productId, Long variantId) {
        if (variantId == null) {
            throw new BusinessException("请选择规格");
        }
        ProductVariant variant = getById(variantId);
        if (variant == null || !productId.equals(variant.getProductId())) {
            throw new BusinessException("规格不可用");
        }
        return variant;
    }
}
