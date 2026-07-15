package com.delta.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.product.entity.Category;
import com.delta.product.entity.Product;
import com.delta.product.entity.RecommendCategory;
import com.delta.product.service.CategoryService;
import com.delta.product.service.ProductService;
import com.delta.product.service.RecommendCategoryService;
import com.delta.product.service.TrialOrderService;
import com.delta.common.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    private final RecommendCategoryService recommendCategoryService;
    private final CategoryService categoryService;
    private final TrialOrderService trialOrderService;

    @GetMapping("/list")
    public R<Page<Product>> list(PageQuery query,
                                  @RequestParam(value = "categoryId", required = false) Long categoryId,
                                  @RequestParam(value = "parentCategoryId", required = false) Long parentCategoryId,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "status", required = false) Integer status,
                                  @RequestParam(value = "orderBy", required = false, defaultValue = "sort") String orderBy) {
        Integer resolvedStatus = status;
        if (resolvedStatus == null && !isPrivilegedRequest()) {
            resolvedStatus = 1;
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(keyword != null, Product::getName, keyword)
                .eq(resolvedStatus != null, Product::getStatus, resolvedStatus);
        // 分类过滤：优先子分类精确匹配，否则按父分类查其下所有子分类商品
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        } else if (parentCategoryId != null) {
            List<Long> childIds = categoryService.list(new LambdaQueryWrapper<Category>()
                    .eq(Category::getParentId, parentCategoryId)
                    .select(Category::getId))
                    .stream().map(Category::getId).toList();
            if (childIds.isEmpty()) {
                // 父分类下无子分类，直接匹配父分类本身
                wrapper.eq(Product::getCategoryId, parentCategoryId);
            } else {
                // 包含父分类本身 + 所有子分类
                childIds = new java.util.ArrayList<>(childIds);
                childIds.add(parentCategoryId);
                wrapper.in(Product::getCategoryId, childIds);
            }
        }
        // 支持多排序
        switch (orderBy) {
            case "sales" -> wrapper.orderByDesc(Product::getSalesCount);
            case "newest" -> wrapper.orderByDesc(Product::getCreatedAt);
            case "price" -> wrapper.orderByAsc(Product::getPrice);
            default -> wrapper.orderByAsc(Product::getSortOrder);
        }
        return R.ok(productService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }

    /** 热门推荐分类列表（用于首页 Tab：全部、护航专区推荐、热门推荐等） */
    @GetMapping("/recommend/categories")
    public R<List<RecommendCategory>> recommendCategories() {
        return R.ok(recommendCategoryService.listAll());
    }

    /** 热门推荐商品，categoryId 为空或不传则查全部推荐，否则只查该分类下的推荐 */
    @GetMapping("/recommend")
    public R<java.util.List<Product>> recommend(
            @RequestParam(value = "categoryId", required = false) Long categoryId) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1).eq(Product::getIsRecommend, 1)
                .orderByAsc(Product::getSortOrder).last("LIMIT 20");
        if (categoryId != null) {
            w.eq(Product::getRecommendCategoryId, categoryId);
        }
        return R.ok(productService.list(w));
    }

    @GetMapping("/{id}")
    public R<Product> detail(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return R.fail("商品不存在");
        }
        if (!isPrivilegedRequest() && !Integer.valueOf(1).equals(product.getStatus())) {
            return R.fail("商品已下架");
        }
        trialOrderService.enrichTrialInfo(product, resolveOptionalUserId());
        productService.fillVariants(product);
        return R.ok(product);
    }

    private Long resolveOptionalUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception ignored) {
            return null;
        }
    }

    @PostMapping
    public R<Void> add(@RequestBody Product product) {
        productService.saveWithVariants(product);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody Product product) {
        productService.updateWithVariants(product);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam("status") Integer status) {
        productService.updateStatus(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { productService.removeById(id); return R.ok(); }

    private boolean isPrivilegedRequest() {
        try {
            String role = SecurityUtils.getRole();
            return "ADMIN".equalsIgnoreCase(role) || "CS".equalsIgnoreCase(role);
        } catch (Exception ignored) {
            return false;
        }
    }
}
