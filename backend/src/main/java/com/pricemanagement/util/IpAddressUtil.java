package com.pricemanagement.util;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;

public final class IpAddressUtil {

    private IpAddressUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    public static boolean isAllowed(String ip, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return true;
        }
        return whitelist.stream()
                .filter(item -> item != null && !item.isBlank())
                .anyMatch(item -> matches(ip, item.trim()));
    }

    private static boolean matches(String ip, String rule) {
        if (rule.equals(ip)) {
            return true;
        }
        if (!rule.contains("/")) {
            return false;
        }
        try {
            String[] parts = rule.split("/", 2);
            byte[] target = InetAddress.getByName(ip).getAddress();
            byte[] base = InetAddress.getByName(parts[0]).getAddress();
            if (target.length != base.length) {
                return false;
            }
            int prefix = Integer.parseInt(parts[1]);
            BigInteger targetInt = new BigInteger(1, target);
            BigInteger baseInt = new BigInteger(1, base);
            int bits = target.length * 8;
            BigInteger mask = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE)
                    .shiftRight(prefix)
                    .not()
                    .and(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE));
            return targetInt.and(mask).equals(baseInt.and(mask));
        } catch (Exception ex) {
            return false;
        }
    }
}
