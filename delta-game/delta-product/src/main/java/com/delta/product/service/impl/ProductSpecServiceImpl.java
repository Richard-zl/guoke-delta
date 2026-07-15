package com.delta.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.product.entity.ProductSpec;
import com.delta.product.mapper.ProductSpecMapper;
import com.delta.product.service.ProductSpecService;
import org.springframework.stereotype.Service;

@Service
public class ProductSpecServiceImpl extends ServiceImpl<ProductSpecMapper, ProductSpec> implements ProductSpecService {}
