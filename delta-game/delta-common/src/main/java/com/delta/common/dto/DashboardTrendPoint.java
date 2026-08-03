package com.delta.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 近 N 日经营趋势单日点 */
@Data
public class DashboardTrendPoint {
    private String date;
    private Long paidOrderCount;
    private BigDecimal gmv;
    private BigDecimal refundAmount;
    private BigDecimal netAmount;
}
