package com.delta.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.product.entity.CategoryFormField;
import com.delta.product.mapper.CategoryFormFieldMapper;
import com.delta.product.service.CategoryFormFieldService;
import org.springframework.stereotype.Service;

@Service
public class CategoryFormFieldServiceImpl extends ServiceImpl<CategoryFormFieldMapper, CategoryFormField>
        implements CategoryFormFieldService {
}
