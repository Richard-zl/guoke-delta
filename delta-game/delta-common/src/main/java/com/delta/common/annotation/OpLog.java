package com.delta.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解 - 标注在需要记录操作日志的 Controller 方法上
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {
    /** 业务模块: order, player, user, withdraw, complaint, system */
    String module();

    /** 操作描述: 指派订单, 审核通过, 冻结打手 等 */
    String operation();
}
