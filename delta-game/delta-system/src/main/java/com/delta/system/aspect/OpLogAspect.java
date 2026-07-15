package com.delta.system.aspect;

import com.delta.common.annotation.OpLog;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.system.entity.OperationLog;
import com.delta.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;

/**
 * 操作日志 AOP 切面
 * 在标注了 @OpLog 的 Controller 方法成功返回后自动写入 operation_log 表
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private final OperationLogService operationLogService;
    private final CrossModuleMapper crossModuleMapper;

    @AfterReturning(pointcut = "@annotation(opLog)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, OpLog opLog, Object result) {
        try {
            recordLog(joinPoint, opLog);
        } catch (Exception e) {
            log.warn("记录操作日志失败: {}", e.getMessage());
        }
    }

    private void recordLog(JoinPoint joinPoint, OpLog opLog) {
        OperationLog record = new OperationLog();
        record.setModule(opLog.module());
        record.setOperation(opLog.operation());
        record.setCreatedAt(LocalDateTime.now());

        // 操作者信息
        try {
            Long userId = SecurityUtils.getUserId();
            String userType = SecurityUtils.getUserType();
            record.setOperatorId(userId);
            record.setOperatorType(userType);
            String nickname = crossModuleMapper.selectAdminNickname(userId);
            record.setOperatorName(nickname != null ? nickname : "ID:" + userId);
        } catch (Exception e) {
            record.setOperatorType("UNKNOWN");
        }

        // 提取目标ID
        extractTarget(joinPoint, record);

        // 详情
        record.setDetail(opLog.operation());

        // 请求IP
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                record.setIp(ip);
            }
        } catch (Exception ignored) {}

        operationLogService.save(record);
    }

    /**
     * 从 @PathVariable 参数中提取第一个数字型参数作为 targetId
     */
    private void extractTarget(JoinPoint joinPoint, OperationLog record) {
        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            Method method = sig.getMethod();
            Parameter[] params = method.getParameters();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < params.length; i++) {
                PathVariable pv = params[i].getAnnotation(PathVariable.class);
                if (pv != null && args[i] instanceof Number) {
                    String name = pv.value().isEmpty() ? params[i].getName() : pv.value();
                    if (record.getTargetId() == null) {
                        record.setTargetId(((Number) args[i]).longValue());
                        record.setTargetType(record.getModule());
                    }
                    if (name.toLowerCase().contains("player")) {
                        record.setTargetType("player");
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
