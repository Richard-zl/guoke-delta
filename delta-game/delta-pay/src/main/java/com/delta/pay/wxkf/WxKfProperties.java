package com.delta.pay.wxkf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信客服（微信客服）相关配置。均允许为空以支持不开通该能力时正常启动；
 * 为空时回调将在日志中明确报错，不影响其余业务。
 */
@Data
@ConfigurationProperties(prefix = "wx.kf")
public class WxKfProperties {
    /** 企业微信 corpId */
    private String corpId = "";
    /** 微信客服 Secret（企业微信管理后台“微信客服”应用获取） */
    private String secret = "";
    /** 回调 URL 验证 Token */
    private String callbackToken = "";
    /** 回调消息加解密 EncodingAESKey（43位） */
    private String callbackAesKey = "";
    /**
     * 微信客服账号 open_kfid（回调日志 / 企微后台可见）。
     * 用于调用 add_contact_way 生成带 enc_scene 的链接；否则 scene_param 不会回传。
     */
    private String openKfid = "";
}
