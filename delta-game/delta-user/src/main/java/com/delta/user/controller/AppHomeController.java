package com.delta.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.product.entity.Category;
import com.delta.product.entity.Product;
import com.delta.product.service.CategoryService;
import com.delta.product.service.ProductService;
import com.delta.system.entity.Banner;
import com.delta.system.entity.Notice;
import com.delta.system.service.BannerService;
import com.delta.system.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户端首页聚合接口
 */
@RestController
@RequestMapping("/app/home")
@RequiredArgsConstructor
public class AppHomeController {
    private final BannerService bannerService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final NoticeService noticeService;

    @GetMapping
    public R<Map<String, Object>> home() {
        Map<String, Object> result = new HashMap<>();
        // 1. 有效Banner（状态启用 + 在有效期内），按排序字段排列
        LocalDateTime now = LocalDateTime.now();
        List<Banner> banners = bannerService.list(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, 1)
                .le(Banner::getStartTime, now)
                .ge(Banner::getEndTime, now)
                .orderByAsc(Banner::getSortOrder));
        result.put("banners", banners);

        // 2. 启用的分类列表
        List<Category> categories = categoryService.list(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder));
        result.put("categories", categories);

        // 3. 热门推荐商品（is_recommend=1，取前10）
        List<Product> hotProducts = productService.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(Product::getIsRecommend, 1)
                .orderByDesc(Product::getSalesCount)
                .last("LIMIT 10"));
        result.put("hotProducts", hotProducts);

        // 4. 最新公告（状态启用，取前5）
        List<Notice> notices = noticeService.list(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .orderByDesc(Notice::getCreatedAt)
                .last("LIMIT 5"));
        result.put("notices", notices);

        return R.ok(result);
    }
}
