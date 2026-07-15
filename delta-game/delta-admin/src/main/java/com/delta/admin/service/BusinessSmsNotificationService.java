package com.delta.admin.service;

import cn.hutool.core.util.StrUtil;
import com.delta.common.event.BusinessEvent;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.sms.service.AliyunSmsService;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 业务短信通知（后台自动发送，不走聊天提醒冷却与扣费）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessSmsNotificationService {
    private static final String CFG_ENABLED = "sms.aliyun.enabled";
    private static final String CFG_ACCESS_KEY_ID = "sms.aliyun.access_key_id";
    private static final String CFG_ACCESS_KEY_SECRET = "sms.aliyun.access_key_secret";
    private static final String CFG_ENDPOINT = "sms.aliyun.endpoint";
    private static final String CFG_SIGN_NAME = "sms.aliyun.sign_name";
    private static final String CFG_PLAYER_ASSIGNED_TEMPLATE_CODE = "sms.aliyun.template_code.player_order_assigned";
    private static final String CFG_PLAYER_TEAMMATE_INVITED_TEMPLATE_CODE = "sms.aliyun.template_code.player_teammate_invited";
    private static final String DEFAULT_ENDPOINT = "dysmsapi.aliyuncs.com";
    private static final String DEFAULT_PLAYER_ASSIGNED_TEMPLATE_CODE = "SMS_504890091";
    private static final String DEFAULT_PLAYER_TEAMMATE_INVITED_TEMPLATE_CODE = "SMS_504775096";

    private final SysConfigService sysConfigService;
    private final CrossModuleMapper crossModuleMapper;
    private final AliyunSmsService aliyunSmsService;

    public void sendIfNeeded(BusinessEvent event) {
        if (event == null || !"PLAYER".equals(event.getUserType()) || event.getUserId() == null) {
            return;
        }

        SmsScene scene = SmsScene.fromEventType(event.getEventType());
        if (scene == null) {
            return;
        }

        if (!"true".equalsIgnoreCase(getConfig(CFG_ENABLED, "false"))) {
            log.debug("business sms skipped because sms is disabled, eventType={}", event.getEventType());
            return;
        }

        String accessKeyId = getConfig(CFG_ACCESS_KEY_ID, "");
        String accessKeySecret = getConfig(CFG_ACCESS_KEY_SECRET, "");
        String endpoint = getConfig(CFG_ENDPOINT, DEFAULT_ENDPOINT);
        String signName = getConfig(CFG_SIGN_NAME, "");
        String templateCode = getConfig(scene.templateConfigKey, scene.defaultTemplateCode);
        if (StrUtil.hasBlank(accessKeyId, accessKeySecret, endpoint, signName, templateCode)) {
            log.warn("business sms skipped because config is incomplete, eventType={}, playerId={}",
                    event.getEventType(), event.getUserId());
            return;
        }

        String phone = crossModuleMapper.selectPlayerPhone(event.getUserId());
        if (StrUtil.isBlank(phone)) {
            log.warn("business sms skipped because player phone is blank, eventType={}, playerId={}",
                    event.getEventType(), event.getUserId());
            return;
        }

        aliyunSmsService.sendTemplate(endpoint, accessKeyId, accessKeySecret, signName,
                templateCode, phone, Map.of());
        log.info("business sms sent, eventType={}, playerId={}, phone={}",
                event.getEventType(), event.getUserId(), phone);
    }

    private String getConfig(String key, String defaultValue) {
        return sysConfigService.getConfigValue(key, defaultValue);
    }

    @RequiredArgsConstructor
    private enum SmsScene {
        PLAYER_ASSIGNED("ORDER_ASSIGNED", CFG_PLAYER_ASSIGNED_TEMPLATE_CODE, DEFAULT_PLAYER_ASSIGNED_TEMPLATE_CODE),
        PLAYER_TEAMMATE_INVITED("TEAMMATE_INVITED",
                CFG_PLAYER_TEAMMATE_INVITED_TEMPLATE_CODE,
                DEFAULT_PLAYER_TEAMMATE_INVITED_TEMPLATE_CODE);

        private final String eventType;
        private final String templateConfigKey;
        private final String defaultTemplateCode;

        private static SmsScene fromEventType(String eventType) {
            if (StrUtil.isBlank(eventType)) {
                return null;
            }
            for (SmsScene scene : values()) {
                if (scene.eventType.equalsIgnoreCase(eventType)) {
                    return scene;
                }
            }
            return null;
        }
    }
}
