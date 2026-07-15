package com.delta.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 管理端列表筛选日期参数解析（支持 yyyy-MM-dd 与 yyyy-MM-dd HH:mm:ss）
 */
public final class QueryDateUtils {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private QueryDateUtils() {
    }

    /** 范围开始：仅日期时取当天 00:00:00 */
    public static LocalDateTime parseStart(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() == 10) {
            return LocalDate.parse(s, DATE).atStartOfDay();
        }
        return LocalDateTime.parse(s, DATETIME);
    }

    /** 范围结束：仅日期时取当天 23:59:59 */
    public static LocalDateTime parseEnd(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() == 10) {
            return LocalDate.parse(s, DATE).atTime(23, 59, 59);
        }
        return LocalDateTime.parse(s, DATETIME);
    }
}
