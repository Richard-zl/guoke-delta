package com.delta.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.order.entity.PlayerReplaceRequest;
import com.delta.order.mapper.PlayerReplaceRequestMapper;
import com.delta.order.service.PlayerReplaceRequestService;
import org.springframework.stereotype.Service;

@Service
public class PlayerReplaceRequestServiceImpl extends ServiceImpl<PlayerReplaceRequestMapper, PlayerReplaceRequest>
        implements PlayerReplaceRequestService {
}
