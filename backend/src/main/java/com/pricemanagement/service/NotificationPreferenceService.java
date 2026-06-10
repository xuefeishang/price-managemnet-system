package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationPreferenceDTO;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationPreference;
import com.pricemanagement.repository.NotificationPreferenceRepository;
import com.pricemanagement.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private static final String CATEGORY_NOTIFICATION_TYPE = "notification_type";
    private static final String CATEGORY_NOTIFICATION_CHANNEL = "notification_channel";

    private final NotificationPreferenceRepository preferenceRepository;
    private final SysDictRepository sysDictRepository;

    @Transactional(readOnly = true)
    public List<NotificationPreferenceDTO> list(Long userId) {
        return preferenceRepository.findByUserIdOrderByNotificationTypeAscChannelAsc(userId).stream()
                .map(NotificationPreferenceDTO::from)
                .toList();
    }

    @Transactional
    public List<NotificationPreferenceDTO> save(Long userId, List<NotificationPreferenceDTO> preferences) {
        for (NotificationPreferenceDTO dto : preferences) {
            validate(dto);
            NotificationPreference preference = preferenceRepository
                    .findByUserIdAndNotificationTypeAndChannel(userId, dto.getNotificationType(), dto.getChannel())
                    .orElseGet(NotificationPreference::new);
            preference.setUserId(userId);
            preference.setNotificationType(dto.getNotificationType());
            preference.setChannel(dto.getChannel());
            preference.setEnabled(resolveEnabled(dto));
            preference.setQuietStartTime(dto.getQuietStartTime());
            preference.setQuietEndTime(dto.getQuietEndTime());
            preferenceRepository.save(preference);
        }
        return list(userId);
    }

    public boolean isExternalChannelEnabled(Long userId, String notificationType, String channel) {
        return resolveExternalDelivery(userId, notificationType, channel,
                NotificationMessage.NotificationPriority.NORMAL, LocalDateTime.now()).enabled();
    }

    @Transactional(readOnly = true)
    public DeliveryPreferenceDecision resolveExternalDelivery(Long userId,
                                                              String notificationType,
                                                              String channel,
                                                              NotificationMessage.NotificationPriority priority,
                                                              LocalDateTime now) {
        if (NotificationService.CHANNEL_IN_APP.equals(channel)) {
            return DeliveryPreferenceDecision.deliverNow();
        }

        NotificationPreference preference = preferenceRepository
                .findByUserIdAndNotificationTypeAndChannel(userId, notificationType, channel)
                .orElse(null);
        if (preference != null && !Boolean.TRUE.equals(preference.getEnabled())) {
            return DeliveryPreferenceDecision.skipped("USER_PREFERENCE_DISABLED", "用户已关闭该类型外部通知渠道");
        }

        QuietWindow quietWindow = resolveQuietWindow(preference, now == null ? LocalDateTime.now() : now);
        if (quietWindow.active()) {
            if (priority == NotificationMessage.NotificationPriority.URGENT) {
                return DeliveryPreferenceDecision.deliverNow("QUIET_HOURS_BYPASSED", "紧急通知绕过免打扰时段");
            }
            return DeliveryPreferenceDecision.delayed(
                    quietWindow.endTime(),
                    "QUIET_HOURS",
                    "当前处于免打扰时段，外部通知将延迟投递");
        }

        return DeliveryPreferenceDecision.deliverNow();
    }

    private Boolean resolveEnabled(NotificationPreferenceDTO dto) {
        if (NotificationService.CHANNEL_IN_APP.equals(dto.getChannel())) {
            return true;
        }
        return dto.getEnabled() == null || dto.getEnabled();
    }

    private void validate(NotificationPreferenceDTO dto) {
        if (dto.getNotificationType() == null || dto.getNotificationType().isBlank()) {
            throw new IllegalArgumentException("通知类型不能为空");
        }
        if (dto.getChannel() == null || dto.getChannel().isBlank()) {
            throw new IllegalArgumentException("通知渠道不能为空");
        }
        if (!isActiveDict(CATEGORY_NOTIFICATION_TYPE, dto.getNotificationType())) {
            throw new IllegalArgumentException("通知类型不存在或未启用");
        }
        if (!isActiveDict(CATEGORY_NOTIFICATION_CHANNEL, dto.getChannel())) {
            throw new IllegalArgumentException("通知渠道不存在或未启用");
        }
        if ((dto.getQuietStartTime() == null) != (dto.getQuietEndTime() == null)) {
            throw new IllegalArgumentException("免打扰开始和结束时间必须同时填写");
        }
    }

    private boolean isActiveDict(String category, String key) {
        return sysDictRepository.findByCategoryAndDictKey(category, key)
                .map(dict -> dict.getStatus() == CommonStatus.ACTIVE)
                .orElse(false);
    }

    private QuietWindow resolveQuietWindow(NotificationPreference preference, LocalDateTime now) {
        if (preference == null
                || preference.getQuietStartTime() == null
                || preference.getQuietEndTime() == null
                || preference.getQuietStartTime().equals(preference.getQuietEndTime())) {
            return QuietWindow.inactive();
        }

        LocalTime current = now.toLocalTime();
        LocalTime start = preference.getQuietStartTime();
        LocalTime end = preference.getQuietEndTime();
        boolean crossDay = start.isAfter(end);
        boolean active = crossDay
                ? !current.isBefore(start) || current.isBefore(end)
                : !current.isBefore(start) && current.isBefore(end);
        if (!active) {
            return QuietWindow.inactive();
        }

        LocalDateTime endTime;
        if (!crossDay) {
            endTime = now.toLocalDate().atTime(end);
        } else if (!current.isBefore(start)) {
            endTime = now.toLocalDate().plusDays(1).atTime(end);
        } else {
            endTime = now.toLocalDate().atTime(end);
        }
        return new QuietWindow(true, endTime);
    }

    public record DeliveryPreferenceDecision(
            boolean enabled,
            boolean delayed,
            LocalDateTime nextDeliveryTime,
            String errorCode,
            String errorMessage
    ) {
        static DeliveryPreferenceDecision deliverNow() {
            return new DeliveryPreferenceDecision(true, false, null, null, null);
        }

        static DeliveryPreferenceDecision deliverNow(String errorCode, String errorMessage) {
            return new DeliveryPreferenceDecision(true, false, null, errorCode, errorMessage);
        }

        static DeliveryPreferenceDecision delayed(LocalDateTime nextDeliveryTime, String errorCode, String errorMessage) {
            return new DeliveryPreferenceDecision(true, true, nextDeliveryTime, errorCode, errorMessage);
        }

        static DeliveryPreferenceDecision skipped(String errorCode, String errorMessage) {
            return new DeliveryPreferenceDecision(false, false, null, errorCode, errorMessage);
        }
    }

    private record QuietWindow(boolean active, LocalDateTime endTime) {
        static QuietWindow inactive() {
            return new QuietWindow(false, null);
        }
    }
}
