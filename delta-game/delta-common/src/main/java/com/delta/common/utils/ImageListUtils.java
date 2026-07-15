package com.delta.common.utils;

import com.alibaba.fastjson2.JSON;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ImageListUtils {
    private ImageListUtils() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            try {
                return join(JSON.parseArray(value, String.class));
            } catch (Exception ignored) {
                // Fall back to comma-separated parsing below.
            }
        }
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return join(Arrays.asList(value.split(",")));
    }

    private static String join(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String image : images) {
            if (image == null) {
                continue;
            }
            String value = image.trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1).trim();
            }
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }
}
