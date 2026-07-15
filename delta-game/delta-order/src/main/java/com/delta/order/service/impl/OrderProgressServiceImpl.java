package com.delta.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.order.entity.OrderProgress;
import com.delta.order.mapper.OrderProgressMapper;
import com.delta.order.service.OrderProgressService;
import org.springframework.stereotype.Service;

@Service
public class OrderProgressServiceImpl extends ServiceImpl<OrderProgressMapper, OrderProgress> implements OrderProgressService {}
