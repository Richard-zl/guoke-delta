package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.R;
import com.delta.product.entity.Category;
import com.delta.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客服端分类管理
 */
@RestController
@RequestMapping("/cs/category")
@RequiredArgsConstructor
public class CsCategoryController {
    private final CategoryService categoryService;

    @GetMapping("/list")
    public R<List<Category>> list() {
        return R.ok(categoryService.list(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder)));
    }

    @OpLog(module = "product", operation = "新增/编辑分类")
    @PostMapping
    public R<Void> save(@RequestBody Category category) {
        categoryService.saveOrUpdate(category);
        return R.ok();
    }

    @OpLog(module = "product", operation = "更新分类状态")
    @PutMapping("/status")
    public R<Void> updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        Category cat = new Category();
        cat.setId(id);
        cat.setStatus(status);
        categoryService.updateById(cat);
        return R.ok();
    }

    @OpLog(module = "product", operation = "删除分类")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.removeById(id);
        return R.ok();
    }
}
