package com.delta.common.sms.service.impl;

import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.delta.common.exception.BusinessException;
import com.delta.common.sms.service.AliyunSmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class AliyunSmsServiceImpl implements AliyunSmsService {

    @Override
    public void sendTemplate(String endpoint,
                             String accessKeyId,
                             String accessKeySecret,
                             String signName,
                             String templateCode,
                             String phoneNumber,
                             Map<String, String> templateParams) {
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.setEndpoint(endpoint);

            Client client = new Client(config);
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phoneNumber)
                    .setSignName(signName)
                    .setTemplateCode(templateCode);
            if (!MapUtil.emptyIfNull(templateParams).isEmpty()) {
                request.setTemplateParam(JSON.toJSONString(templateParams));
            }

            SendSmsResponse response = client.sendSms(request);
            String code = response.getBody() != null ? response.getBody().getCode() : null;
            String message = response.getBody() != null ? response.getBody().getMessage() : null;
            if (!"OK".equalsIgnoreCase(code)) {
                log.warn("aliyun sms send failed, phone={}, code={}, message={}", phoneNumber, code, message);
                throw new BusinessException(500, message != null && !message.isEmpty() ? message : "短信发送失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("aliyun sms send error, phone={}", phoneNumber, e);
            throw new BusinessException(500, "短信发送失败，请稍后重试");
        }
    }
}
