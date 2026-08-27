package com.delta.common.util;

/**
 * 安全解析打手最大并发接单数。
 */
public final class MaxConcurrentConfigParser {

    private static final int DEFAULT_MAX_CONCURRENT = 1;

    private MaxConcurrentConfigParser() {
    }

    public static int parse(String rawValue) {
        try {
            int parsed = Integer.parseInt(rawValue);
            return parsed > 0 ? parsed : DEFAULT_MAX_CONCURRENT;
        } catch (NumberFormatException | NullPointerException ignored) {
            return DEFAULT_MAX_CONCURRENT;
        }
    }
}
