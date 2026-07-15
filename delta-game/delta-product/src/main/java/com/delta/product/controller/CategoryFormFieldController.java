package com.delta.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.product.entity.Category;
import com.delta.product.entity.CategoryFormField;
import com.delta.product.service.CategoryFormFieldService;
import com.delta.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product/category")
@RequiredArgsConstructor
public class CategoryFormFieldController {
    private final CategoryFormFieldService categoryFormFieldService;
    private final CategoryService categoryService;

    /**
     * 查询某分类的动态表单字段
     * 如果传入的是子分类ID，会自动向上找到父分类的字段配置
     */
    @GetMapping("/{categoryId}/form-fields")
    public R<List<CategoryFormField>> getFormFields(@PathVariable Long categoryId) {
        Long parentId = resolveParentCategoryId(categoryId);
        List<CategoryFormField> fields = categoryFormFieldService.list(
                new LambdaQueryWrapper<CategoryFormField>()
                        .eq(CategoryFormField::getCategoryId, parentId)
                        .orderByAsc(CategoryFormField::getSortOrder));
        return R.ok(fields);
    }

    /**
     * 如果 categoryId 本身是父分类(parentId==0或null)则返回自身，否则返回其 parentId
     */
    private Long resolveParentCategoryId(Long categoryId) {
        Category cat = categoryService.getById(categoryId);
        if (cat == null) return categoryId;
        return (cat.getParentId() == null || cat.getParentId() == 0) ? cat.getId() : cat.getParentId();
    }
}
