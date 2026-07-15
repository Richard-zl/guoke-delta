package com.delta.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.product.entity.PriceRule;
import com.delta.product.mapper.PriceRuleMapper;
import com.delta.product.service.PriceRuleService;
import org.springframework.stereotype.Service;

@Service
public class PriceRuleServiceImpl extends ServiceImpl<PriceRuleMapper, PriceRule> implements PriceRuleService {}
