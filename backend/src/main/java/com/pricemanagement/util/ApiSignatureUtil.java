package com.pricemanagement.util;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class ApiSignatureUtil {

    public static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private ApiSignatureUtil() {
    }

    public static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
        } catch (Exception ex) {
            throw new IllegalStateException("计算SHA-256失败", ex);
        }
    }

    public static String hmacSha256Hex(String secret, String canonicalString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("计算HMAC-SHA256失败", ex);
        }
    }

    public static boolean constantTimeEquals(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        byte[] expected = expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualHex.toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public static String canonicalString(HttpServletRequest request, String timestamp, String nonce, String bodySha256Hex) {
        return String.join("\n",
                request.getMethod().toUpperCase(),
                request.getRequestURI(),
                canonicalQuery(request.getQueryString()),
                timestamp,
                nonce,
                bodySha256Hex);
    }

    public static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] keyValue = pair.split("=", 2);
            String key = decode(keyValue[0]);
            String value = keyValue.length > 1 ? decode(keyValue[1]) : "";
            parts.add(encode(key) + "=" + encode(value));
        }
        Collections.sort(parts);
        return String.join("&", parts);
    }

    public static String canonicalQuery(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                parts.add(encode(key) + "=");
                continue;
            }
            List<String> sortedValues = new ArrayList<>(Arrays.asList(values));
            Collections.sort(sortedValues);
            for (String value : sortedValues) {
                parts.add(encode(key) + "=" + encode(value == null ? "" : value));
            }
        }
        Collections.sort(parts);
        return String.join("&", parts);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
