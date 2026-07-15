package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.system.entity.ContentConfig;
import com.delta.system.service.ContentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/system/content")
@RequiredArgsConstructor
public class ContentConfigController {
    private final ContentConfigService contentConfigService;

    @GetMapping("/list")
    public R<List<ContentConfig>> list() {
        return R.ok(contentConfigService.list());
    }

    @GetMapping("/key/{key}")
    public R<ContentConfig> getByKey(@PathVariable String key) {
        return R.ok(contentConfigService.getOne(
                new LambdaQueryWrapper<ContentConfig>().eq(ContentConfig::getConfigKey, key)));
    }

    @PostMapping
    public R<Void> add(@RequestBody ContentConfig config) { contentConfigService.save(config); return R.ok(); }

    @PutMapping
    public R<Void> update(@RequestBody ContentConfig config) { contentConfigService.updateById(config); return R.ok(); }
}
