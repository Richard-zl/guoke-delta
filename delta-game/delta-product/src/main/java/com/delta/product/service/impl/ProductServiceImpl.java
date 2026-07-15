package com.delta.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.utils.ImageListUtils;
import com.delta.product.entity.Product;
import com.delta.product.enums.ProductLimitTypeEnum;
import com.delta.product.mapper.ProductMapper;
import com.delta.product.service.ProductConfigNormalizer;
import com.delta.product.service.ProductService;
import com.delta.product.service.ProductVariantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final ProductVariantService productVariantService;

    @Override
    public boolean save(Product product) {
        normalizeProduct(product);
        return super.save(product);
    }

    @Override
    public boolean updateById(Product product) {
        normalizeProduct(product);
        return super.updateById(product);
    }

    @Override
    public boolean saveOrUpdate(Product product) {
        normalizeProduct(product);
        return super.saveOrUpdate(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveWithVariants(Product product) {
        save(product);
        productVariantService.replaceVariants(product.getId(), product.getVariants());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWithVariants(Product product) {
        updateById(product);
        productVariantService.replaceVariants(product.getId(), product.getVariants());
    }

    @Override
    public void fillVariants(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        product.setVariants(productVariantService.listActiveByProductId(product.getId()));
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        super.updateById(product);
    }

    private void normalizeProduct(Product product) {
        if (product == null) {
            return;
        }
        product.setImages(ImageListUtils.normalize(product.getImages()));
        normalizePerUserLimit(product);
        ProductConfigNormalizer.normalizeQuantity(product);
    }

    private void normalizePerUserLimit(Product product) {
        ProductLimitTypeEnum limitType = ProductLimitTypeEnum.resolve(
                product.getPerUserLimitType(),
                product.getPerUserLimitEnabled(),
                product.getPerUserLimitCount());
        product.setPerUserLimitType(limitType.getCode());
        if (!limitType.isLimited()) {
            product.setPerUserLimitEnabled(0);
            product.setPerUserLimitCount(null);
            return;
        }
        product.setPerUserLimitEnabled(1);
        product.setPerUserLimitCount(1);
    }
}
