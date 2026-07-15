package com.delta.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("statistics_daily")
public class StatisticsDaily {
    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate statDate;
    private Integer newUserCount;
    private Integer activeUserCount;
    private Integer newOrderCount;
    private Integer completedOrderCount;
    private Integer cancelledOrderCount;
    private BigDecimal totalAmount;
    private BigDecimal platformIncome;
    private BigDecimal playerIncome;
    private Integer newPlayerCount;
    private Integer activePlayerCount;
    private Integer newComplaintCount;
    private BigDecimal avgOrderAmount;
    private LocalDateTime createdAt;
}
