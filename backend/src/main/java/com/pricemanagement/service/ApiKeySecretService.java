package com.pricemanagement.service;

import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.util.ApiSignatureUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ApiKeySecretService {

    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final ApiKeyProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateAppId() {
        byte[] random = new byte[18];
        secureRandom.nextBytes(random);
        return "app_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public String generateSecret() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        return "sec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public String encrypt(String secret) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("API Secret加密失败", ex);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("API Secret解密失败", ex);
        }
    }

    public String fingerprint(String secret) {
        return ApiSignatureUtil.sha256Hex(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private SecretKey masterKey() {
        byte[] keyBytes = Base64.getDecoder().decode(properties.getEncryptionKey());
        return new SecretKeySpec(keyBytes, "AES");
    }
}
