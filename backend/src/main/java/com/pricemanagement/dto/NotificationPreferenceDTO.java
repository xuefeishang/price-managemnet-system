package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationPreference;
import lombok.Data;

import java.time.LocalTime;

@Data
public class NotificationPreferenceDTO {
    private Long id;
    private String notificationType;
    private String channel;
    private Boolean enabled;
    private LocalTime quietStartTime;
    private LocalTime quietEndTime;

    public static NotificationPreferenceDTO from(NotificationPreference preference) {
        NotificationPreferenceDTO dto = new NotificationPreferenceDTO();
        dto.setId(preference.getId());
        dto.setNotificationType(preference.getNotificationType());
        dto.setChannel(preference.getChannel());
        dto.setEnabled(preference.getEnabled());
        dto.setQuietStartTime(preference.getQuietStartTime());
        dto.setQuietEndTime(preference.getQuietEndTime());
        return dto;
    }
}
