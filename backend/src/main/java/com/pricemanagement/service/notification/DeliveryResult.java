package com.pricemanagement.service.notification;

public record DeliveryResult(
        boolean success,
        boolean skipped,
        String providerMessageId,
        String errorCode,
        String errorMessage
) {

    public static DeliveryResult success(String providerMessageId) {
        return new DeliveryResult(true, false, providerMessageId, null, null);
    }

    public static DeliveryResult skipped(String errorCode, String errorMessage) {
        return new DeliveryResult(false, true, null, errorCode, errorMessage);
    }

    public static DeliveryResult failed(String errorCode, String errorMessage) {
        return new DeliveryResult(false, false, null, errorCode, errorMessage);
    }
}
