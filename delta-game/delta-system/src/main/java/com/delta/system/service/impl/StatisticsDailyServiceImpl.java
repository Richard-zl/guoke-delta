package com.delta.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.system.entity.StatisticsDaily;
import com.delta.system.mapper.StatisticsDailyMapper;
import com.delta.system.service.StatisticsDailyService;
import org.springframework.stereotype.Service;

@Service
public class StatisticsDailyServiceImpl extends ServiceImpl<StatisticsDailyMapper, StatisticsDaily> implements StatisticsDailyService {}
