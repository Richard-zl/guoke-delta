package com.delta.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.product.entity.Category;
import com.delta.product.entity.Product;
import com.delta.product.enums.TrialLimitPeriodEnum;
import com.delta.product.service.CategoryService;
import com.delta.product.service.TrialOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TrialOrderServiceImpl implements TrialOrderService {

    private final CategoryService categoryService;
    private final CrossModuleMapper crossModuleMapper;

    @Override
    public boolean isTrialProduct(Product product) {
        if (product == null || product.getCategoryId() == null) {
            return false;
        }
        return resolveTrialCategoryIds().contains(product.getCategoryId());
    }

    @Override
    public void validateNoCoupon(Product product, Long couponId) {
        if (couponId != null && isTrialProduct(product)) {
            throw new BusinessException("体验单不可使用优惠券");
        }
    }

    @Override
    public void validateTrialOrderForCreate(Long userId, Product product) {
        validateTrialLimit(userId, product, null);
    }

    @Override
    public void validateTrialOrderForPay(Long userId, Long orderId, Product product) {
        validateTrialLimit(userId, product, orderId);
    }

    @Override
    public void enrichTrialInfo(Product product, Long userId) {
        if (product == null) {
            return;
        }
        if (!isTrialProduct(product)) {
            product.setCouponAllowed(true);
            return;
        }
        product.setCouponAllowed(false);
        TrialLimitPeriodEnum period = resolveLimitPeriod();
        if (!period.isLimited()) {
            product.setTrialLimitReached(false);
            product.setTrialLimitTip("");
            return;
        }
        product.setTrialLimitTip(period.getLimitTip());
        if (userId == null) {
            product.setTrialLimitReached(false);
            return;
        }
        int paidCount = countPaidTrialOrders(userId, period, null);
        product.setTrialLimitReached(paidCount > 0);
    }

    private void validateTrialLimit(Long userId, Product product, Long excludeOrderId) {
        if (!isTrialProduct(product)) {
            return;
        }
        TrialLimitPeriodEnum period = resolveLimitPeriod();
        if (!period.isLimited()) {
            return;
        }
        int paidCount = countPaidTrialOrders(userId, period, excludeOrderId);
        if (paidCount > 0) {
            throw new BusinessException(period.getExceededMessage());
        }
    }

    private int countPaidTrialOrders(Long userId, TrialLimitPeriodEnum period, Long excludeOrderId) {
        LocalDateTime windowStart = period.resolveWindowStart(LocalDateTime.now());
        List<Long> categoryIds = new ArrayList<>(resolveTrialCategoryIds());
        return crossModuleMapper.countUserPaidTrialOrdersSince(
                userId, categoryIds, windowStart, excludeOrderId);
    }

    /**
     * 解析体验单覆盖的商品分类 ID：根分类 + 所有层级子孙 + 配置的额外分类
     */
    private Set<Long> resolveTrialCategoryIds() {
        long rootId = resolveRootCategoryId();
        Set<Long> ids = new HashSet<>();
        ids.add(rootId);

        List<Category> all = categoryService.list(
                new LambdaQueryWrapper<Category>().eq(Category::getDeleted, 0));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Category category : all) {
                Long parentId = category.getParentId();
                if (parentId != null && parentId > 0 && ids.contains(parentId) && ids.add(category.getId())) {
                    changed = true;
                }
            }
        }

        parseAdditionalCategoryIds().forEach(ids::add);
        return ids;
    }

    private Set<Long> parseAdditionalCategoryIds() {
        String value = crossModuleMapper.selectConfigValue(CONFIG_ADDITIONAL_CATEGORY_IDS);
        Set<Long> ids = new HashSet<>();
        if (value == null || value.isBlank()) {
            return ids;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 忽略非法配置项
            }
        }
        return ids;
    }

    private long resolveRootCategoryId() {
        String value = crossModuleMapper.selectConfigValue(CONFIG_ROOT_CATEGORY_ID);
        if (value == null || value.isBlank()) {
            return DEFAULT_ROOT_CATEGORY_ID;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_ROOT_CATEGORY_ID;
        }
    }

    private TrialLimitPeriodEnum resolveLimitPeriod() {
        String value = crossModuleMapper.selectConfigValue(CONFIG_LIMIT_PERIOD);
        if (value == null || value.isBlank()) {
            return TrialLimitPeriodEnum.DAY_1;
        }
        try {
            return TrialLimitPeriodEnum.fromDays(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return TrialLimitPeriodEnum.DAY_1;
        }
    }
}
