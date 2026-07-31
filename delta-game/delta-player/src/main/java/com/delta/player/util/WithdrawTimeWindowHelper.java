package com.delta.player.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public final class WithdrawTimeWindowHelper {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] DOW_CN = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private WithdrawTimeWindowHelper() {}

    public record Window(int startDow, LocalTime startTime, int endDow, LocalTime endTime) {}

    public static List<Window> defaultWindows() {
        return List.of(
                new Window(2, LocalTime.of(12, 0), 3, LocalTime.of(12, 0)),
                new Window(6, LocalTime.of(12, 0), 7, LocalTime.of(12, 0))
        );
    }

    public static List<Window> parseWindows(String json) {
        if (json == null || json.isBlank()) return defaultWindows();
        try {
            List<Map<String, Object>> raw = MAPPER.readValue(json, new TypeReference<>() {});
            List<Window> list = new ArrayList<>();
            for (Map<String, Object> m : raw) {
                int startDow = ((Number) m.get("startDow")).intValue();
                int endDow = ((Number) m.get("endDow")).intValue();
                LocalTime startTime = LocalTime.parse(String.valueOf(m.get("startTime")));
                LocalTime endTime = LocalTime.parse(String.valueOf(m.get("endTime")));
                list.add(new Window(startDow, startTime, endDow, endTime));
            }
            if (list.isEmpty()) return defaultWindows();
            return list;
        } catch (Exception e) {
            log.warn("withdraw.time_windows 解析失败，使用默认窗口: {}", e.getMessage());
            return defaultWindows();
        }
    }

    public static boolean isInWindow(LocalDateTime now, List<Window> windows) {
        if (windows == null || windows.isEmpty()) return false;
        int dow = now.getDayOfWeek().getValue();
        LocalTime t = now.toLocalTime();
        long nowMin = (dow - 1L) * 24 * 60 + t.getHour() * 60L + t.getMinute();
        for (Window w : windows) {
            long startMin = (w.startDow() - 1L) * 24 * 60
                    + w.startTime().getHour() * 60L + w.startTime().getMinute();
            long endMin = (w.endDow() - 1L) * 24 * 60
                    + w.endTime().getHour() * 60L + w.endTime().getMinute();
            if (endMin <= startMin) {
                long weekEnd = 7L * 24 * 60;
                if ((nowMin >= startMin && nowMin < weekEnd) || (nowMin >= 0 && nowMin < endMin)) {
                    return true;
                }
            } else if (nowMin >= startMin && nowMin < endMin) {
                return true;
            }
        }
        return false;
    }

    public static String buildWindowsText(List<Window> windows) {
        if (windows == null || windows.isEmpty()) windows = defaultWindows();
        StringBuilder sb = new StringBuilder("每周");
        for (int i = 0; i < windows.size(); i++) {
            Window w = windows.get(i);
            if (i > 0) sb.append("、");
            sb.append(DOW_CN[w.startDow()]).append(' ')
                    .append(w.startTime()).append('–')
                    .append(DOW_CN[w.endDow()]).append(' ').append(w.endTime());
        }
        return sb.toString();
    }
}
