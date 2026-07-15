package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.system.entity.OperationLog;
import com.delta.system.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/operation-log")
@RequiredArgsConstructor
public class OperationLogController {
    private final OperationLogService operationLogService;

    @GetMapping("/list")
    public R<Page<OperationLog>> list(PageQuery query,
                                       @RequestParam(value = "module", required = false) String module,
                                       @RequestParam(value = "operatorType", required = false) String operatorType) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(module != null, OperationLog::getModule, module)
                .eq(operatorType != null, OperationLog::getOperatorType, operatorType)
                .orderByDesc(OperationLog::getCreatedAt);
        return R.ok(operationLogService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }
}
