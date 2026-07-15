package com.delta.product.service;

import com.delta.product.entity.RecommendCategory;

import java.util.List;

public interface RecommendCategoryService {
    List<RecommendCategory> listAll();

    void save(RecommendCategory entity);

    void updateById(RecommendCategory entity);

    void removeById(Long id);
}
