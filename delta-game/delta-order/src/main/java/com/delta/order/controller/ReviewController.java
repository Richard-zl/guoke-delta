package com.delta.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.utils.ImageListUtils;
import com.delta.order.entity.Order;
import com.delta.order.entity.Review;
import com.delta.order.service.OrderService;
import com.delta.order.service.ReviewService;
import com.delta.product.entity.Product;
import com.delta.product.service.ProductService;
import com.delta.common.event.BusinessEvent;
import com.delta.common.mapper.CrossModuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/order/review")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;
    private final OrderService orderService;
    private final ProductService productService;
    private final CrossModuleMapper crossModuleMapper;
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping
    public R<Void> add(@RequestBody Review review) {
        Long userId = SecurityUtils.getUserId();
        review.setUserId(userId);

        // 1. 订单状态校验：必须是CONFIRMED状态
        Order order = orderService.getById(review.getOrderId());
        if (order == null) return R.fail("订单不存在");
        if (!OrderStatusEnum.CONFIRMED.name().equals(order.getStatus())) {
            return R.fail("订单状态不允许评价");
        }
        if (!order.getUserId().equals(userId)) return R.fail("无权评价");

        // 2. 检查是否已评价
        long existCount = reviewService.count(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, review.getOrderId())
                .eq(Review::getUserId, userId));
        if (existCount > 0) return R.fail("已评价，不可重复");

        // 3. 简单敏感词过滤
        review.setContent(filterSensitiveWords(review.getContent()));

        review.setPlayerId(order.getPlayerId());
        review.setProductId(order.getProductId());
        review.setImages(ImageListUtils.normalize(review.getImages()));
        review.setCreatedAt(LocalDateTime.now());
        reviewService.save(review);

        // 4. 更新订单状态→REVIEWED
        orderService.markReviewed(order.getId());

        // 5. 更新商品评价数和平均评分
        updateProductRating(order.getProductId());

        // 6. 更新主接打手平均评分
        if (order.getPlayerId() != null) {
            updatePlayerRating(order.getPlayerId());
            // 7. 通知主接打手收到新评价
            eventPublisher.publishEvent(new BusinessEvent(this, "NEW_REVIEW",
                    "PLAYER", order.getPlayerId(), order.getId(), "您收到一条新评价，评分: " + review.getRating()));
        }

        return R.ok();
    }

    private String filterSensitiveWords(String content) {
        if (content == null) return null;
        // 简单敏感词替换，实际应用DFA算法或第三方库
        String[] sensitiveWords = {"开挂", "外挂", "代练骗子"};
        for (String word : sensitiveWords) {
            content = content.replace(word, "***");
        }
        return content;
    }

    private void updateProductRating(Long productId) {
        List<Review> reviews = reviewService.list(new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId));
        if (!reviews.isEmpty()) {
            BigDecimal avg = reviews.stream()
                    .map(r -> BigDecimal.valueOf(r.getRating()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(reviews.size()), 2, RoundingMode.HALF_UP);
            Product product = productService.getById(productId);
            product.setAvgRating(avg);
            product.setReviewCount(reviews.size());
            productService.updateById(product);
        }
    }

    private void updatePlayerRating(Long playerId) {
        // 使用CrossModuleMapper避免delta-order→delta-player循环依赖
        crossModuleMapper.updatePlayerAvgRating(playerId);
    }

    @GetMapping("/my")
    public R<Page<Review>> myReviews(PageQuery query) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(reviewService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Review>().eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreatedAt)));
    }

    @GetMapping("/product/{productId}")
    public R<Page<Review>> byProduct(@PathVariable Long productId, PageQuery query) {
        return R.ok(reviewService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Review>().eq(Review::getProductId, productId)
                        .orderByDesc(Review::getCreatedAt)));
    }

    @GetMapping("/player/{playerId}")
    public R<Page<Review>> byPlayer(@PathVariable Long playerId, PageQuery query) {
        return R.ok(reviewService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Review>().eq(Review::getPlayerId, playerId)
                        .orderByDesc(Review::getCreatedAt)));
    }
}
