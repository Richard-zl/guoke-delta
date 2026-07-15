package com.delta.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.product.entity.ProductVariant;

import java.util.List;

public interface ProductVariantService extends IService<ProductVariant> {

    /** 查询商品下有效规格选项，按 sort_order 升序 */
    List<ProductVariant> listActiveByProductId(Long productId);

    /** 全量覆盖保存规格选项（逻辑删除旧数据后插入） */
    void replaceVariants(Long productId, List<ProductVariant> variants);

    /** 校验规格属于商品且有效，否则抛 BusinessException */
    ProductVariant requireActiveVariant(Long productId, Long variantId);
}
