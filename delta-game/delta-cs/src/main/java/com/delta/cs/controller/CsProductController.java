package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.product.entity.Product;
import com.delta.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cs/product")
@RequiredArgsConstructor
public class CsProductController {
    private final ProductService productService;

    @GetMapping("/list")
    public R<Page<Product>> list(PageQuery query,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(status != null, Product::getStatus, status)
                .like(keyword != null, Product::getName, keyword)
                .orderByDesc(Product::getCreatedAt);
        return R.ok(productService.page(new Page<>(query.getPageNum(), query.getPageSize()), w));
    }

    @GetMapping("/{id}")
    public R<Product> detail(@PathVariable Long id) {
        return R.ok(productService.getById(id));
    }

    @OpLog(module = "product", operation = "新增/编辑商品")
    @PostMapping
    public R<Void> save(@RequestBody Product product) {
        productService.saveOrUpdate(product);
        return R.ok();
    }
}
