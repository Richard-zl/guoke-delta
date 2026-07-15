package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.system.entity.QuickReply;
import com.delta.system.service.QuickReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/system/quick-reply")
@RequiredArgsConstructor
public class QuickReplyController {
    private final QuickReplyService quickReplyService;

    @GetMapping("/list")
    public R<Page<QuickReply>> list(PageQuery query, @RequestParam(value = "category", required = false) String category) {
        LambdaQueryWrapper<QuickReply> wrapper = new LambdaQueryWrapper<QuickReply>()
                .eq(category != null, QuickReply::getCategory, category)
                .orderByAsc(QuickReply::getSortOrder);
        return R.ok(quickReplyService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }

    @GetMapping("/active")
    public R<List<QuickReply>> active(@RequestParam(value = "category", required = false) String category) {
        return R.ok(quickReplyService.list(new LambdaQueryWrapper<QuickReply>()
                .eq(QuickReply::getStatus, 1)
                .eq(category != null, QuickReply::getCategory, category)
                .orderByAsc(QuickReply::getSortOrder)));
    }

    @PostMapping
    public R<Void> add(@RequestBody QuickReply reply) { quickReplyService.save(reply); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody QuickReply reply) { quickReplyService.updateById(reply); return R.ok(); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { quickReplyService.removeById(id); return R.ok(); }
}
