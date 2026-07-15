package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.system.entity.Banner;
import com.delta.system.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/system/banner")
@RequiredArgsConstructor
public class BannerController {
    private final BannerService bannerService;

    @GetMapping("/list")
    public R<Page<Banner>> list(PageQuery query) {
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSortOrder);
        return R.ok(bannerService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }

    @GetMapping("/active")
    public R<List<Banner>> active() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, 1)
                .and(w -> w.isNull(Banner::getStartTime).or().le(Banner::getStartTime, now))
                .and(w -> w.isNull(Banner::getEndTime).or().ge(Banner::getEndTime, now))
                .orderByAsc(Banner::getSortOrder);
        return R.ok(bannerService.list(wrapper));
    }

    @PostMapping
    public R<Void> add(@RequestBody Banner banner) { bannerService.save(banner); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody Banner banner) { bannerService.updateById(banner); return R.ok(); }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) { bannerService.removeById(id); return R.ok(); }
}
