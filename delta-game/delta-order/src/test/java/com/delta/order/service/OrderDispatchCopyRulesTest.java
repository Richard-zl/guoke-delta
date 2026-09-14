package com.delta.order.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDispatchCopyRulesTest {

    @Test
    void childCategory_usesParentAsTypeAndSelfAsPlatform() {
        assertEquals("保底单", OrderDispatchCopyRules.firstCategoryName("端游", 38L, "保底单"));
        assertEquals("端游", OrderDispatchCopyRules.platformName("端游", 38L, "保底单", "双护保底单（端游）"));
    }

    @Test
    void rootCategory_usesSelfAsType_andInfersPlatformFromProductName() {
        assertEquals("保底单", OrderDispatchCopyRules.firstCategoryName("保底单", 0L, null));
        assertEquals("端游", OrderDispatchCopyRules.platformName("保底单", 0L, null, "双护保底单（端游）·1000W"));
        assertEquals("手游", OrderDispatchCopyRules.platformName("保底单", null, null, "护航单（手游）"));
        assertEquals("", OrderDispatchCopyRules.platformName("保底单", 0L, null, "普通商品"));
    }

    @Test
    void customZone_mapsToTaskOrder() {
        assertEquals("任务单", OrderDispatchCopyRules.firstCategoryName("定制专区", 0L, null));
        assertEquals("任务单", OrderDispatchCopyRules.firstCategoryName("端游", 10L, "定制专区"));
        assertEquals("双端均可", OrderDispatchCopyRules.platformName("定制专区", 0L, null, "任意商品"));
        assertEquals("双端均可", OrderDispatchCopyRules.platformName("端游", 10L, "定制专区", "双护（端游）"));
        assertEquals(true, OrderDispatchCopyRules.blankDispatchDetail("定制专区", 0L, null));
        assertEquals(true, OrderDispatchCopyRules.blankDispatchDetail("端游", 10L, "定制专区"));
        assertEquals(false, OrderDispatchCopyRules.blankDispatchDetail("保底单", 0L, null));
        assertEquals("保底单", OrderDispatchCopyRules.firstCategoryName("保底单", 0L, null));
    }
}
