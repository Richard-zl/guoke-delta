package com.delta.order.service;

/**
 * 管理后台「复制信息派单」用的分类解析。
 * 类型取一级分类；平台取子分类，没有子分类时从商品名推断端游/手游。
 */
public final class OrderDispatchCopyRules {

    private OrderDispatchCopyRules() {
    }

    public static String firstCategoryName(String categoryName, Long parentId, String parentName) {
        return mapTypeName(rawFirstCategory(categoryName, parentId, parentName));
    }

    /** 派单「类型」展示名：后台一级分类 → 业务用语 */
    static String mapTypeName(String firstCategoryName) {
        if (isCustomZone(firstCategoryName)) {
            return "任务单";
        }
        return firstCategoryName;
    }

    public static String platformName(String categoryName, Long parentId, String parentName, String productName) {
        if (isCustomZone(rawFirstCategory(categoryName, parentId, parentName))) {
            return "双端均可";
        }
        if (hasParent(parentId)) {
            return blankToEmpty(categoryName);
        }
        return inferPlatform(productName);
    }

    /** 定制专区派单不写商品详情 */
    public static boolean blankDispatchDetail(String categoryName, Long parentId, String parentName) {
        return isCustomZone(rawFirstCategory(categoryName, parentId, parentName));
    }

    static String inferPlatform(String productName) {
        if (productName == null || productName.isBlank()) {
            return "";
        }
        if (productName.contains("端游")) {
            return "端游";
        }
        if (productName.contains("手游")) {
            return "手游";
        }
        return "";
    }

    private static String rawFirstCategory(String categoryName, Long parentId, String parentName) {
        return hasParent(parentId) ? blankToEmpty(parentName) : blankToEmpty(categoryName);
    }

    private static boolean isCustomZone(String firstCategoryName) {
        return "定制专区".equals(firstCategoryName);
    }

    private static boolean hasParent(Long parentId) {
        return parentId != null && parentId > 0;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
