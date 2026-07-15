package com.delta.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.product.entity.Product;

public interface ProductService extends IService<Product> {
    /** 上下架 */
    void updateStatus(Long id, Integer status);

    /** 新增商品并保存规格选项 */
    void saveWithVariants(Product product);

    /** 更新商品并保存规格选项 */
    void updateWithVariants(Product product);

    /** 填充商品规格选项列表 */
    void fillVariants(Product product);
}
