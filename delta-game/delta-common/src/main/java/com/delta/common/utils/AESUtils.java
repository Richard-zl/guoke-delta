package com.delta.common.utils;

import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AESUtils {
    private static AES aes;

    @Value("${aes.key}")
    private String aesKey;

    public void init() {
        aes = new AES(aesKey.getBytes(StandardCharsets.UTF_8));
    }

    public static String encrypt(String data) { return aes.encryptHex(data); }
    public static String decrypt(String data) { return aes.decryptStr(data); }
}
