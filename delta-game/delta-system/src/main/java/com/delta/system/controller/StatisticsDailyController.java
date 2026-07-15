package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.system.entity.StatisticsDaily;
import com.delta.system.service.StatisticsDailyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/system/statistics")
@RequiredArgsConstructor
public class StatisticsDailyController {
    private final StatisticsDailyService statisticsDailyService;

    @GetMapping("/daily")
    public R<List<StatisticsDaily>> daily(
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return R.ok(statisticsDailyService.list(new LambdaQueryWrapper<StatisticsDaily>()
                .between(StatisticsDaily::getStatDate, startDate, endDate)
                .orderByAsc(StatisticsDaily::getStatDate)));
    }

    @GetMapping("/latest")
    public R<StatisticsDaily> latest() {
        return R.ok(statisticsDailyService.getOne(new LambdaQueryWrapper<StatisticsDaily>()
                .orderByDesc(StatisticsDaily::getStatDate).last("LIMIT 1")));
    }
}
