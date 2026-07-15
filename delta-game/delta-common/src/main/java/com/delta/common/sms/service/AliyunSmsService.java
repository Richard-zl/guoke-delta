package com.delta.common.sms.service;

import java.util.Map;

public interface AliyunSmsService {
    void sendTemplate(String endpoint,
                      String accessKeyId,
                      String accessKeySecret,
                      String signName,
                      String templateCode,
                      String phoneNumber,
                      Map<String, String> templateParams);
}
