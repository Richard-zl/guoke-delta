package com.delta.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.product.entity.Category;
import com.delta.product.entity.Product;
import com.delta.product.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialOrderServiceImplTest {

    /** 生产环境：超值体验单根分类及子分类 */
    private static final long TRIAL_ROOT_ID = 38L;
    private static final long TRIAL_PC_ID = 46L;
    private static final long TRIAL_MOBILE_ID = 54L;
    private static final long OTHER_ROOT_ID = 39L;

    @Mock
    private CategoryService categoryService;
    @Mock
    private CrossModuleMapper crossModuleMapper;

    private TrialOrderServiceImpl trialOrderService;

    @BeforeEach
    void setUp() {
        trialOrderService = new TrialOrderServiceImpl(categoryService, crossModuleMapper);
    }

    @Test
    void isTrialProduct_根分类商品_返回true() {
        mockTrialConfig();
        when(categoryService.list(any(LambdaQueryWrapper.class))).thenReturn(trialCategoryTree());

        Product product = new Product();
        product.setCategoryId(TRIAL_ROOT_ID);

        assertTrue(trialOrderService.isTrialProduct(product));
    }

    @Test
    void isTrialProduct_端游子分类商品_返回true() {
        mockTrialConfig();
        when(categoryService.list(any(LambdaQueryWrapper.class))).thenReturn(trialCategoryTree());

        Product product = new Product();
        product.setCategoryId(TRIAL_PC_ID);

        assertTrue(trialOrderService.isTrialProduct(product));
    }

    @Test
    void isTrialProduct_手游子分类商品_返回true() {
        mockTrialConfig();
        when(categoryService.list(any(LambdaQueryWrapper.class))).thenReturn(trialCategoryTree());

        Product product = new Product();
        product.setCategoryId(TRIAL_MOBILE_ID);

        assertTrue(trialOrderService.isTrialProduct(product));
    }

    @Test
    void isTrialProduct_非体验单分类_返回false() {
        mockTrialConfig();
        when(categoryService.list(any(LambdaQueryWrapper.class))).thenReturn(trialCategoryTree());

        Product product = new Product();
        product.setCategoryId(OTHER_ROOT_ID);

        assertFalse(trialOrderService.isTrialProduct(product));
    }

    @Test
    void validateNoCoupon_体验单带优惠券_抛出异常() {
        mockTrialConfig();
        when(categoryService.list(any(LambdaQueryWrapper.class))).thenReturn(trialCategoryTree());

        Product product = new Product();
        product.setCategoryId(TRIAL_PC_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trialOrderService.validateNoCoupon(product, 100L));
        assertEquals("体验单不可使用优惠券", ex.getMessage());
    }

    @Test
    void validateTrialOrderForCreate_周期内已有体验单_抛出异常() {
        mockTrialConfig();
        when(crossModuleMapper.selectConfigValue("trial.limit_period")).thenReturn("1");
        when(categoryService.list(any(LambdaQueryWrapper.class))).thenReturn(trialCategoryTree());
        when(crossModuleMapper.countUserPaidTrialOrdersSince(eq(1L), anyList(), any(), isNull()))
                .thenReturn(1);

        Product product = new Product();
        product.setCategoryId(TRIAL_MOBILE_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trialOrderService.validateTrialOrderForCreate(1L, product));
        assertTrue(ex.getMessage().contains("体验单"));
    }

    @Test
    void validateTrialOrderForPay_已有其他已付体验单_抛出异常() {
        mockTrialConfig();
        when(crossModuleMapper.selectConfigValue("trial.limit_period")).thenReturn("1");
        when(categoryService.list(any(LambdaQueryWrapper.class))).thenReturn(trialCategoryTree());
        when(crossModuleMapper.countUserPaidTrialOrdersSince(eq(1L), anyList(), any(), eq(200L)))
                .thenReturn(1);

        Product product = new Product();
        product.setCategoryId(TRIAL_PC_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> trialOrderService.validateTrialOrderForPay(1L, 200L, product));
        assertTrue(ex.getMessage().contains("体验单"));
    }

    private void mockTrialConfig() {
        when(crossModuleMapper.selectConfigValue("trial.root_category_id")).thenReturn("38");
        when(crossModuleMapper.selectConfigValue("trial.additional_category_ids")).thenReturn("");
    }

    /** 模拟后台分类树：38 超值体验单 → 46 端游、54 手游 */
    private static List<Category> trialCategoryTree() {
        return List.of(
                category(TRIAL_ROOT_ID, 0L),
                category(TRIAL_PC_ID, TRIAL_ROOT_ID),
                category(TRIAL_MOBILE_ID, TRIAL_ROOT_ID),
                category(OTHER_ROOT_ID, 0L)
        );
    }

    private static Category category(long id, long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setDeleted(0);
        return category;
    }
}
