package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.system.entity.Admin;
import com.delta.system.entity.OperationLog;
import com.delta.system.service.AdminService;
import com.delta.system.service.OperationLogService;
import com.delta.system.support.AdminRoleCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/system/operation-log")
@RequiredArgsConstructor
public class OperationLogController {
    private final OperationLogService operationLogService;
    private final AdminService adminService;

    @GetMapping("/list")
    public R<Page<OperationLog>> list(PageQuery query,
                                       @RequestParam(value = "module", required = false) String module,
                                       @RequestParam(value = "operatorType", required = false) String operatorType) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<OperationLog>()
                .eq(StringUtils.hasText(module), OperationLog::getModule, module)
                .orderByDesc(OperationLog::getCreatedAt);
        if (StringUtils.hasText(operatorType)) {
            List<Long> adminIds = findAdminIds(operatorType);
            if (adminIds.isEmpty()) {
                wrapper.eq(OperationLog::getId, -1L);
            } else {
                // 按账号当前角色筛，店长和运营以前记成管理员的日志也能查到
                wrapper.in(OperationLog::getOperatorId, adminIds);
            }
        }
        Page<OperationLog> page = operationLogService.page(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        fillOperatorRole(page.getRecords());
        return R.ok(page);
    }

    private List<Long> findAdminIds(String operatorType) {
        String dbRole = AdminRoleCodes.toDbRole(operatorType);
        if (!StringUtils.hasText(dbRole)) {
            return List.of();
        }
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<Admin>().select(Admin::getId);
        if ("admin".equals(dbRole)) {
            wrapper.and(w -> w.apply("LOWER(role) = {0}", "admin").or().apply("LOWER(role) = {0}", "both"));
        } else {
            wrapper.apply("LOWER(role) = {0}", dbRole);
        }
        return adminService.list(wrapper).stream().map(Admin::getId).toList();
    }

    /** 列表展示账号上的角色，而不是当时令牌里的管理员/客服。 */
    private void fillOperatorRole(List<OperationLog> records) {
        List<Long> ids = records.stream().map(OperationLog::getOperatorId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Admin> admins = adminService.listByIds(ids).stream()
                .collect(Collectors.toMap(Admin::getId, Function.identity(), (a, b) -> a));
        for (OperationLog record : records) {
            Admin admin = admins.get(record.getOperatorId());
            if (admin == null) {
                continue;
            }
            String code = AdminRoleCodes.toCode(admin.getRole());
            if (StringUtils.hasText(code)) {
                record.setOperatorType(code);
            }
        }
    }
}
