package com.delta.message.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import com.alibaba.fastjson2.JSON;
import com.delta.message.entity.SubscribeMessageLog;
import com.delta.message.service.SubscribeMessageLogService;
import com.delta.message.service.WxSubscribeMessageService;
import com.delta.player.entity.Player;
import com.delta.player.service.PlayerService;
import com.delta.user.entity.User;
import com.delta.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WxSubscribeMessageServiceImpl implements WxSubscribeMessageService {

    private final ObjectProvider<WxMaService> wxMaServiceProvider;
    private final UserService userService;
    private final PlayerService playerService;
    private final SubscribeMessageLogService subscribeMessageLogService;

    @Async
    @Override
    public void sendAsync(String userType, Long userId, String templateId,
                          Map<String, String> data, String page) {
        if (templateId == null || templateId.startsWith("YOUR_TEMPLATE_ID")) {
            log.debug("模板ID未配置，跳过微信订阅消息: templateId={}", templateId);
            return;
        }

        WxMaService wxMaService = wxMaServiceProvider.getIfAvailable();
        if (wxMaService == null) {
            log.debug("微信小程序未启用，跳过订阅消息: templateId={}", templateId);
            return;
        }

        String openid = resolveOpenid(userType, userId);
        if (openid == null || openid.isEmpty()) {
            log.warn("未找到openid，跳过微信订阅消息: userType={}, userId={}", userType, userId);
            return;
        }

        // 构建订阅消息
        WxMaSubscribeMessage message = new WxMaSubscribeMessage();
        message.setToUser(openid);
        message.setTemplateId(templateId);
        message.setPage(page);
        message.setMiniprogramState("formal"); // formal=正式版, developer=开发版, trial=体验版

        List<WxMaSubscribeMessage.MsgData> msgDataList = new ArrayList<>();
        if (data != null) {
            data.forEach((key, value) -> {
                WxMaSubscribeMessage.MsgData msgData = new WxMaSubscribeMessage.MsgData();
                msgData.setName(key);
                msgData.setValue(value);
                msgDataList.add(msgData);
            });
        }
        message.setData(msgDataList);

        // 发送并记录日志
        SubscribeMessageLog logEntity = new SubscribeMessageLog();
        logEntity.setUserType(userType);
        logEntity.setUserId(userId);
        logEntity.setTemplateId(templateId);
        logEntity.setData(JSON.toJSONString(data));
        logEntity.setCreatedAt(LocalDateTime.now());

        try {
            wxMaService.getMsgService().sendSubscribeMsg(message);
            logEntity.setStatus("SUCCESS");
            log.info("微信订阅消息发送成功: userType={}, userId={}, template={}", userType, userId, templateId);
        } catch (Exception e) {
            logEntity.setStatus("FAIL");
            logEntity.setErrorMsg(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 490)) : "unknown");
            log.error("微信订阅消息发送失败: userType={}, userId={}, template={}, error={}",
                    userType, userId, templateId, e.getMessage());
        }

        try {
            subscribeMessageLogService.save(logEntity);
        } catch (Exception e) {
            log.error("保存订阅消息日志失败", e);
        }
    }

    /**
     * 根据 userType 查询对应的 openid
     */
    private String resolveOpenid(String userType, Long userId) {
        if (userId == null) return null;
        if ("USER".equals(userType)) {
            User user = userService.getById(userId);
            return user != null ? user.getOpenid() : null;
        } else if ("PLAYER".equals(userType)) {
            Player player = playerService.getById(userId);
            return player != null ? player.getOpenid() : null;
        }
        return null;
    }
}
