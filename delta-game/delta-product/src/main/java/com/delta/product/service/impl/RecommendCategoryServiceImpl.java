package com.delta.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.product.entity.RecommendCategory;
import com.delta.product.mapper.RecommendCategoryMapper;
import com.delta.product.service.RecommendCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendCategoryServiceImpl implements RecommendCategoryService {
    private final RecommendCategoryMapper recommendCategoryMapper;

    @Override
    public List<RecommendCategory> listAll() {
        return recommendCategoryMapper.selectList(
                new LambdaQueryWrapper<RecommendCategory>().orderByAsc(RecommendCategory::getSortOrder));
    }

    @Override
    public void save(RecommendCategory entity) {
        recommendCategoryMapper.insert(entity);
    }

    @Override
    public void updateById(RecommendCategory entity) {
        recommendCategoryMapper.updateById(entity);
    }

    @Override
    public void removeById(Long id) {
        recommendCategoryMapper.deleteById(id);
    }
}
