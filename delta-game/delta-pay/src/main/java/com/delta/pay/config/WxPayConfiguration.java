package com.delta.pay.config;

import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.refund.RefundService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "wx.pay", name = "enabled", havingValue = "true")
public class WxPayConfiguration {

    @Getter
    @Value("${wx.miniapp.appid:}")
    private String appId;

    @Getter
    @Value("${wx.mp.appid:}")
    private String mpAppId;

    @Getter
    @Value("${wx.pay.mch-id:}")
    private String mchId;

    @Getter
    @Value("${wx.pay.notify-url:}")
    private String notifyUrl;

    @Value("${wx.pay.api-v3-key:}")
    private String apiV3Key;

    @Value("${wx.pay.private-key-path:}")
    private String privateKeyPath;

    @Value("${wx.pay.cert-serial-no:}")
    private String certSerialNo;

    @Value("${wx.pay.public-key-id:}")
    private String publicKeyId;

    @Value("${wx.pay.public-key-path:}")
    private String publicKeyPath;

    @Bean
    public Config wxPayConfig() {
        return new RSAPublicKeyConfig.Builder()
                .merchantId(mchId)
                .privateKeyFromPath(privateKeyPath)
                .merchantSerialNumber(certSerialNo)
                .apiV3Key(apiV3Key)
                .publicKeyId(publicKeyId)
                .publicKeyFromPath(publicKeyPath)
                .build();
    }

    @Bean
    public JsapiServiceExtension jsapiServiceExtension(Config config) {
        return new JsapiServiceExtension.Builder().config(config).build();
    }

    @Bean
    public RefundService refundService(Config config) {
        return new RefundService.Builder().config(config).build();
    }

    @Bean
    public NotificationParser notificationParser(Config config) {
        return new NotificationParser((NotificationConfig) config);
    }
}
