package com.delta.pay.wxkf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信客服回调：GET 用于企业微信后台配置时的 URL 有效性验证；POST 接收实际消息/事件通知。
 * 已在 SecurityConfig 放行 /pay/wxkf/callback（鉴权改由微信签名机制承担，见 {@link WxKfCrypt}）。
 */
@Slf4j
@RestController
@RequestMapping("/pay/wxkf")
@RequiredArgsConstructor
public class WxKfController {

    /** 解密后明文既可能是 JSON，也可能是 XML（线上实测为 XML） */
    private static final Pattern XML_TOKEN = Pattern.compile(
            "<Token>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</Token>", Pattern.DOTALL);
    private static final Pattern XML_OPEN_KF_ID = Pattern.compile(
            "<OpenKfId>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</OpenKfId>", Pattern.DOTALL);

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
        // 只要企微推到了本机，这条日志必现；没有则说明回调 URL 未配/未验证/被 Nginx 拦
        log.info("收到微信客服回调POST bodyLen={}", body == null ? 0 : body.length());
        try {
            String decrypted = wxKfCrypt.decryptPostBody(msgSignature, timestamp, nonce, body);
            String kfToken = null;
            String openKfId = null;
            String trimmed = decrypted == null ? "" : decrypted.trim();
            if (trimmed.startsWith("<")) {
                // 解密结果为 XML：<Token>/<OpenKfId>
                kfToken = matchGroup(XML_TOKEN, trimmed);
                openKfId = matchGroup(XML_OPEN_KF_ID, trimmed);
            } else {
                // 官方文档示例为 JSON
                JsonNode node = objectMapper.readTree(trimmed);
                kfToken = textOrNull(node, "Token");
                openKfId = textOrNull(node, "OpenKfId");
            }
            log.info("客服回调解密成功 OpenKfId={}, hasToken={}", openKfId, kfToken != null && !kfToken.isBlank());
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

    private static String textOrNull(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String matchGroup(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return null;
        }
        String v = m.group(1);
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
