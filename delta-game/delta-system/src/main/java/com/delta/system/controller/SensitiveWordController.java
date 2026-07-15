package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.system.entity.SensitiveWord;
import com.delta.system.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/sensitive-word")
@RequiredArgsConstructor
public class SensitiveWordController {
    private final SensitiveWordService sensitiveWordService;

    @GetMapping("/list")
    public R<Page<SensitiveWord>> list(PageQuery query, @RequestParam(value = "category", required = false) String category) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<SensitiveWord>()
                .eq(category != null, SensitiveWord::getCategory, category)
                .orderByDesc(SensitiveWord::getCreatedAt);
        return R.ok(sensitiveWordService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }

    @PostMapping
    public R<Void> add(@RequestBody SensitiveWord word) { sensitiveWordService.save(word); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody SensitiveWord word) { sensitiveWordService.updateById(word); return R.ok(); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { sensitiveWordService.removeById(id); return R.ok(); }
}
