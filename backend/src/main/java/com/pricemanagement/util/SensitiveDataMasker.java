package com.pricemanagement.util;

import java.util.regex.Pattern;

public final class SensitiveDataMasker {

    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(secret|password|token|access[_-]?token|app[_-]?secret)(\\s*[=:]\\s*)([^,}\\]\\s]+)");
    private static final Pattern URL_SECRET = Pattern.compile(
            "(?i)([?&](?:secret|access_token|token)=)([^&\\s]+)");

    private SensitiveDataMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = NAMED_SECRET.matcher(value).replaceAll("$1$2******");
        return URL_SECRET.matcher(masked).replaceAll("$1******");
    }
}
