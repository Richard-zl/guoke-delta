package com.delta.pay.wxkf;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用 {@link WxKfProperties} 的 @ConfigurationProperties 绑定 */
@Configuration
@EnableConfigurationProperties(WxKfProperties.class)
public class WxKfAutoConfiguration {
}
