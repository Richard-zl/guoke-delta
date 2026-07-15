package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.system.entity.Notice;
import com.delta.system.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @GetMapping("/list")
    public R<Page<Notice>> list(PageQuery query, @RequestParam(value = "type", required = false) String type) {
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<Notice>()
                .eq(type != null, Notice::getType, type)
                .orderByAsc(Notice::getSortOrder);
        return R.ok(noticeService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }

    @GetMapping("/active")
    public R<List<Notice>> active() {
        return R.ok(noticeService.list(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1).orderByAsc(Notice::getSortOrder)));
    }

    @PostMapping
    public R<Void> add(@RequestBody Notice notice) { noticeService.save(notice); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody Notice notice) { noticeService.updateById(notice); return R.ok(); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { noticeService.removeById(id); return R.ok(); }
}
