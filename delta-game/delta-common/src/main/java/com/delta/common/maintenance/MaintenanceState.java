package com.delta.common.maintenance;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全局开关状态：控制接口是否对外开放。
 * true  表示正常对外提供服务
 * false 表示维护模式，除维护页面外所有接口拒绝访问
 *
 * 使用静态 AtomicBoolean，确保在任何上下文/Bean 实例中状态一致。
 */
@Component
public class MaintenanceState {

    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);

    public boolean isEnabled() {
        return ENABLED.get();
    }

    public void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }
}


