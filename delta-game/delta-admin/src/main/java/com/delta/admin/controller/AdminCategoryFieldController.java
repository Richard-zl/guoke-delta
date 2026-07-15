package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.product.entity.CategoryFormField;
import com.delta.product.service.CategoryFormFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/category-field")
@RequiredArgsConstructor
public class AdminCategoryFieldController {
    private final CategoryFormFieldService categoryFormFieldService;

    @GetMapping("/list")
    public R<List<CategoryFormField>> list(@RequestParam Long categoryId) {
        return R.ok(categoryFormFieldService.list(
                new LambdaQueryWrapper<CategoryFormField>()
                        .eq(CategoryFormField::getCategoryId, categoryId)
                        .orderByAsc(CategoryFormField::getSortOrder)));
    }

    @PostMapping
    public R<Void> add(@RequestBody CategoryFormField field) {
        categoryFormFieldService.save(field);
        return R.ok();
    }

    @PutMapping
    public R<Void> update(@RequestBody CategoryFormField field) {
        categoryFormFieldService.updateById(field);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        categoryFormFieldService.removeById(id);
        return R.ok();
    }
}
