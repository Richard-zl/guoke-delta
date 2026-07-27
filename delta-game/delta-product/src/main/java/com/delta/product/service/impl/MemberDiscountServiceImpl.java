package com.delta.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.enums.MemberLevelEnum;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.product.dto.MemberDiscountResult;
import com.delta.product.entity.Category;
import com.delta.product.entity.Product;
import com.delta.product.service.CategoryService;
import com.delta.product.service.MemberDiscountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberDiscountServiceImpl implements MemberDiscountService {

    private final CategoryService categoryService;
    private final CrossModuleMapper crossModuleMapper;

    @Override
    public boolean isDiscountExcluded(Product product) {
        if (product == null || product.getCategoryId() == null) {
            return false;
        }
        return resolveExcludeCategoryIds().contains(product.getCategoryId());
    }

    @Override
    public MemberDiscountResult applyLevelDiscount(BigDecimal subtotal, Long userId, Product product) {
        BigDecimal original = normalizeMoney(subtotal == null ? BigDecimal.ZERO : subtotal);
        if (userId == null) {
            return noDiscount(original, null, "未登录");
        }
        if (isDiscountExcluded(product)) {
            MemberLevelEnum level = resolveUserLevel(userId);
            return MemberDiscountResult.builder()
                    .originalAmount(original)
                    .amountAfterMemberDiscount(original)
                    .discountRate(BigDecimal.ONE)
                    .discountAmount(BigDecimal.ZERO)
                    .levelName(level.getName())
                    .levelCode(level.getCode())
                    .applied(false)
                    .skipReason("该商品不参与会员折扣")
                    .build();
        }

        MemberLevelEnum level = resolveUserLevel(userId);
        if (!level.hasDiscount()) {
            return noDiscount(original, level, null);
        }

        BigDecimal after = normalizeMoney(original.multiply(level.getDiscountRate()));
        BigDecimal discountAmount = original.subtract(after);
        return MemberDiscountResult.builder()
                .originalAmount(original)
                .amountAfterMemberDiscount(after)
                .discountRate(level.getDiscountRate())
                .discountAmount(discountAmount)
                .levelName(level.getName())
                .levelCode(level.getCode())
                .applied(true)
                .build();
    }

    @Override
    public void enrichMemberDiscountInfo(Product product, Long userId) {
        if (product == null) {
            return;
        }
        boolean excluded = isDiscountExcluded(product);
        product.setMemberDiscountAllowed(!excluded);
        if (userId == null) {
            product.setMemberDiscountRate(BigDecimal.ONE);
            product.setMemberLevelName(null);
            product.setMemberLevelCode(null);
            return;
        }
        MemberLevelEnum level = resolveUserLevel(userId);
        product.setMemberLevelCode(level.getCode());
        product.setMemberLevelName(level.getName());
        if (excluded || !level.hasDiscount()) {
            product.setMemberDiscountRate(BigDecimal.ONE);
            return;
        }
        product.setMemberDiscountRate(level.getDiscountRate());
    }

    private MemberDiscountResult noDiscount(BigDecimal original, MemberLevelEnum level, String skipReason) {
        return MemberDiscountResult.builder()
                .originalAmount(original)
                .amountAfterMemberDiscount(original)
                .discountRate(BigDecimal.ONE)
                .discountAmount(BigDecimal.ZERO)
                .levelName(level != null ? level.getName() : null)
                .levelCode(level != null ? level.getCode() : null)
                .applied(false)
                .skipReason(skipReason)
                .build();
    }

    private MemberLevelEnum resolveUserLevel(Long userId) {
        Integer totalPoints = crossModuleMapper.selectUserTotalPoints(userId);
        String levelCode = crossModuleMapper.selectUserLevelCode(userId);
        if (totalPoints != null) {
            return MemberLevelEnum.getByPoints(totalPoints);
        }
        return MemberLevelEnum.getByCode(levelCode);
    }

    /** 解析排除分类：配置的根分类 + 所有子孙 */
    private Set<Long> resolveExcludeCategoryIds() {
        Set<Long> roots = parseExcludeCategoryIds();
        if (roots.isEmpty()) {
            return roots;
        }
        Set<Long> ids = new HashSet<>(roots);
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
        return ids;
    }

    private Set<Long> parseExcludeCategoryIds() {
        String value = crossModuleMapper.selectConfigValue(CONFIG_EXCLUDE_CATEGORY_IDS);
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
                // 忽略非法配置
            }
        }
        return ids;
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
