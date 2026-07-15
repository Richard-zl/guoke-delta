package com.delta.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信订阅消息模板ID配置
 * 对应 application.yml 中 wx.miniapp.subscribe-template.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "wx.miniapp.subscribe-template")
public class WxTemplateProperties {
    /** 订单状态变更通知 */
    private String orderStatus;
    /** 收入到账通知 */
    private String income;
    /** 组队邀请通知 */
    private String teamInvite;
    /** 订单确认通知 */
    private String orderConfirm;
}
