package com.delta.pay.wxkf;

import com.delta.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业微信（微信客服属于企业微信应用体系）回调消息加解密。
 * 算法与企业微信官方 WXBizMsgCrypt 完全一致：AES-256-CBC + PKCS7 padding，
 * 签名为 sha1(sort(token, timestamp, nonce, encrypt纯文本))。
 */
@Slf4j
@Component
public class WxKfCrypt {

    private static final Pattern ENCRYPT_TAG_PATTERN =
            Pattern.compile("<Encrypt><!\\[CDATA\\[(.*?)]]></Encrypt>", Pattern.DOTALL);

    private final WxKfProperties properties;

    public WxKfCrypt(WxKfProperties properties) {
        this.properties = properties;
    }

    /**
     * URL 验证：校验签名后解密 echostr，返回需原样 echo 给微信的明文。
     */
    public String verifyUrl(String msgSignature, String timestamp, String nonce, String echostr) {
        checkConfigured();
        verifySignature(msgSignature, timestamp, nonce, echostr);
        return decryptToMsg(echostr);
    }

    /**
     * 解密回调 POST 请求体（企业微信标准 XML 信封，<Encrypt> 为 CDATA 密文）。
     * 微信客服的解密结果为 JSON 字符串（含 Token / OpenKfId），非 XML。
     */
    public String decryptPostBody(String msgSignature, String timestamp, String nonce, String postBodyXml) {
        checkConfigured();
        String encrypt = extractEncrypt(postBodyXml);
        verifySignature(msgSignature, timestamp, nonce, encrypt);
        return decryptToMsg(encrypt);
    }

    private void checkConfigured() {
        if (isBlank(properties.getCorpId()) || isBlank(properties.getCallbackToken())
                || isBlank(properties.getCallbackAesKey())) {
            throw new BusinessException("企业微信客服回调未配置完整（corp-id/callback-token/callback-aes-key）");
        }
    }

    private String extractEncrypt(String xml) {
        if (xml == null) {
            throw new BusinessException("回调请求体为空");
        }
        Matcher matcher = ENCRYPT_TAG_PATTERN.matcher(xml);
        if (!matcher.find()) {
            throw new BusinessException("回调请求体缺少Encrypt字段");
        }
        return matcher.group(1);
    }

    private void verifySignature(String msgSignature, String timestamp, String nonce, String encrypt) {
        String expected = sign(properties.getCallbackToken(), timestamp, nonce, encrypt);
        if (msgSignature == null || !expected.equals(msgSignature)) {
            throw new BusinessException("回调签名校验失败");
        }
    }

    private String sign(String token, String timestamp, String nonce, String encrypt) {
        String[] items = {token, timestamp, nonce, encrypt};
        Arrays.sort(items);
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append(item);
        }
        return sha1Hex(sb.toString());
    }

    private String sha1Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException("签名计算失败");
        }
    }

    /**
     * 解密 base64 密文，剥离 [16字节随机数][4字节网络字节序长度][正文][receiveId]，返回正文明文。
     */
    private String decryptToMsg(String encryptedBase64) {
        try {
            byte[] aesKey = Base64.getDecoder().decode(properties.getCallbackAesKey() + "=");
            byte[] cipherBytes = Base64.getDecoder().decode(encryptedBase64);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), iv);
            byte[] decrypted = cipher.doFinal(cipherBytes);

            byte[] plain = removePkcs7Padding(decrypted);

            byte[] lenBytes = Arrays.copyOfRange(plain, 16, 20);
            int msgLen = bytesToInt(lenBytes);
            String msg = new String(plain, 20, msgLen, StandardCharsets.UTF_8);

            String receiveId = new String(plain, 20 + msgLen, plain.length - 20 - msgLen, StandardCharsets.UTF_8);
            if (!properties.getCorpId().equals(receiveId)) {
                log.warn("企业微信回调receiveId({})与配置corp-id不一致，请核对配置", receiveId);
            }
            return msg;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("企业微信回调消息解密失败", e);
            throw new BusinessException("回调消息解密失败");
        }
    }

    /**
     * 加密明文（供需要主动构造加密回复的场景使用；当前回调统一走 kf API 主动发消息，暂不使用，保留以保持算法完整）。
     */
    public String encrypt(String plainMsg) {
        checkConfigured();
        try {
            byte[] aesKey = Base64.getDecoder().decode(properties.getCallbackAesKey() + "=");
            byte[] randomBytes = new byte[16];
            new SecureRandom().nextBytes(randomBytes);
            byte[] msgBytes = plainMsg.getBytes(StandardCharsets.UTF_8);
            byte[] receiveIdBytes = properties.getCorpId().getBytes(StandardCharsets.UTF_8);
            byte[] lenBytes = intToBytes(msgBytes.length);

            byte[] combined = new byte[randomBytes.length + lenBytes.length + msgBytes.length + receiveIdBytes.length];
            int pos = 0;
            System.arraycopy(randomBytes, 0, combined, pos, randomBytes.length); pos += randomBytes.length;
            System.arraycopy(lenBytes, 0, combined, pos, lenBytes.length); pos += lenBytes.length;
            System.arraycopy(msgBytes, 0, combined, pos, msgBytes.length); pos += msgBytes.length;
            System.arraycopy(receiveIdBytes, 0, combined, pos, receiveIdBytes.length);

            byte[] padded = addPkcs7Padding(combined);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), iv);
            byte[] encrypted = cipher.doFinal(padded);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("企业微信回调消息加密失败", e);
            throw new BusinessException("回调消息加密失败");
        }
    }

    private static byte[] removePkcs7Padding(byte[] data) {
        int pad = data[data.length - 1];
        if (pad < 1 || pad > 32) {
            pad = 0;
        }
        return Arrays.copyOfRange(data, 0, data.length - pad);
    }

    private static byte[] addPkcs7Padding(byte[] data) {
        int blockSize = 32;
        int amountToPad = blockSize - (data.length % blockSize);
        if (amountToPad == 0) {
            amountToPad = blockSize;
        }
        byte[] result = new byte[data.length + amountToPad];
        System.arraycopy(data, 0, result, 0, data.length);
        Arrays.fill(result, data.length, result.length, (byte) amountToPad);
        return result;
    }

    private static int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24 & 0xFF),
                (byte) (value >> 16 & 0xFF),
                (byte) (value >> 8 & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
