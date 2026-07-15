package com.delta.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.order.entity.OrderRelayRequest;
import com.delta.order.mapper.OrderRelayRequestMapper;
import com.delta.order.service.OrderRelayRequestService;
import org.springframework.stereotype.Service;

@Service
public class OrderRelayRequestServiceImpl extends ServiceImpl<OrderRelayRequestMapper, OrderRelayRequest> implements OrderRelayRequestService {
}
