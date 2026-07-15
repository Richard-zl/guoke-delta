package com.delta.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.mapper.OrderPlayerMapper;
import com.delta.order.service.OrderPlayerService;
import org.springframework.stereotype.Service;

@Service
public class OrderPlayerServiceImpl extends ServiceImpl<OrderPlayerMapper, OrderPlayer> implements OrderPlayerService {}
