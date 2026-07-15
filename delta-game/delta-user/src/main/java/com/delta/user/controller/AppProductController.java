package com.delta.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.product.entity.Category;
import com.delta.product.entity.Product;
import com.delta.product.service.CategoryService;
import com.delta.product.service.ProductService;
import com.delta.product.service.TrialOrderService;
import com.delta.common.security.utils.SecurityUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端商品详情（含评价概要）
 */
@RestController
@RequestMapping("/app/product")
@RequiredArgsConstructor
public class AppProductController {
    private final ProductService productService;
    private final CategoryService categoryService;
    private final TrialOrderService trialOrderService;

    /**
     * 商品列表（支持分类筛选、排序、分页）
     */
    @GetMapping("/list")
    public R<Page<Product>> list(PageQuery query,
                                 @RequestParam(value = "categoryId", required = false) Long categoryId,
                                 @RequestParam(value = "sort", defaultValue = "default") String sort) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(categoryId != null, Product::getCategoryId, categoryId);
        switch (sort) {
            case "sales" -> w.orderByDesc(Product::getSalesCount);
            default -> w.orderByAsc(Product::getSortOrder);
        }
        return R.ok(productService.page(new Page<>(query.getPageNum(), query.getPageSize()), w));
    }

    @GetMapping("/{id}")
    public R<AppProductDetailVO> detail(@PathVariable Long id) {
        AppProductDetailVO vo = new AppProductDetailVO();
        Product product = productService.getById(id);
        if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
            return R.fail("商品不存在");
        }
        vo.setProduct(product);

        // 所属分类名称
        if (product.getCategoryId() != null) {
            Category category = categoryService.getById(product.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        trialOrderService.enrichTrialInfo(product, resolveOptionalUserId());
        productService.fillVariants(product);
        return R.ok(vo);
    }

    private Long resolveOptionalUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    @Data
    public static class AppProductDetailVO {
        private Product product;
        private String categoryName;
    }
}
