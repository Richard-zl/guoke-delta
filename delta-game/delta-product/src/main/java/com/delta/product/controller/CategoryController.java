package com.delta.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.product.entity.Category;
import com.delta.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/product/category")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping("/tree")
    public R<List<Category>> tree() {
        List<Category> all = categoryService.list(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder));
        List<Category> parents = all.stream().filter(c -> c.getParentId() == null || c.getParentId() == 0).collect(Collectors.toList());
        Map<Long, List<Category>> childMap = all.stream().filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Category::getParentId));
        for (Category p : parents) {
            p.setChildren(childMap.getOrDefault(p.getId(), Collections.emptyList()));
        }
        return R.ok(parents);
    }

    @GetMapping("/list")
    public R<List<Category>> list() {
        return R.ok(categoryService.list(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1).orderByAsc(Category::getSortOrder)));
    }

    @GetMapping("/all")
    public R<List<Category>> all() {
        return R.ok(categoryService.list(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder)));
    }

    @PostMapping
    public R<Void> add(@RequestBody Category cat) { categoryService.save(cat); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody Category cat) { categoryService.updateById(cat); return R.ok(); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { categoryService.removeById(id); return R.ok(); }
}
