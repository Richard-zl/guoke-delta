package com.delta.pay.wxkf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 微信客服回调：GET 用于企业微信后台配置时的 URL 有效性验证；POST 接收实际消息/事件通知。
 * 已在 SecurityConfig 放行 /pay/wxkf/callback（鉴权改由微信签名机制承担，见 {@link WxKfCrypt}）。
 */
@Slf4j
@RestController
@RequestMapping("/pay/wxkf")
@RequiredArgsConstructor
public class WxKfController {

    private final WxKfCrypt wxKfCrypt;
    private final WxKfService wxKfService;
    private final ObjectMapper objectMapper;

    @GetMapping("/callback")
    public String verifyUrl(@RequestParam("msg_signature") String msgSignature,
                             @RequestParam String timestamp,
                             @RequestParam String nonce,
                             @RequestParam String echostr) {
        return wxKfCrypt.verifyUrl(msgSignature, timestamp, nonce, echostr);
    }

    @PostMapping("/callback")
    public String receiveEvent(@RequestParam("msg_signature") String msgSignature,
                                @RequestParam String timestamp,
                                @RequestParam String nonce,
                                @RequestBody String body) {
        try {
            String decrypted = wxKfCrypt.decryptPostBody(msgSignature, timestamp, nonce, body);
            JsonNode node = objectMapper.readTree(decrypted);
            String kfToken = node.path("Token").asText(null);
            String openKfId = node.path("OpenKfId").asText(null);
            if (kfToken == null || openKfId == null) {
                log.warn("客服回调解密内容缺少Token/OpenKfId: {}", decrypted);
                return "success";
            }
            wxKfService.syncAndDispatch(kfToken, openKfId);
        } catch (Exception e) {
            // 回调必须始终返回success，避免微信判定失败后重试风暴；异常已在内部记录日志
            log.error("处理微信客服回调失败", e);
        }
        return "success";
    }
}
