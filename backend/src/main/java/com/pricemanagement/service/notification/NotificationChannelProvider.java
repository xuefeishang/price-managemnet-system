package com.pricemanagement.service.notification;

import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;

public interface NotificationChannelProvider {

    String channel();

    DeliveryResult send(NotificationMessage message, NotificationDeliveryLog delivery);
}
