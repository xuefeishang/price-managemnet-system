package com.pricemanagement.util;

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;

public final class IpAddressUtil {

    private IpAddressUtil() {
    }

    public static String getClientIp(HttpServletRequest request) {
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

    public static boolean matchesAny(String ip, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        return rules.stream()
                .filter(item -> item != null && !item.isBlank())
                .anyMatch(item -> matches(ip, item.trim()));
    }

    public static boolean matches(String ip, String rule) {
        if (ip == null || ip.isBlank() || rule == null || rule.isBlank()) {
            return false;
        }
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
