package com.delta.common.utils;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

public class IdGenerator {
    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);
    public static long nextId() { return SNOWFLAKE.nextId(); }
    public static String nextIdStr() { return SNOWFLAKE.nextIdStr(); }
    public static String nextOrderNo() { return "O" + SNOWFLAKE.nextIdStr(); }
    public static String nextPaymentNo() { return "P" + SNOWFLAKE.nextIdStr(); }
}
