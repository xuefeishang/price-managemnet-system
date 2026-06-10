package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationPreferenceDTO;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationPreference;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.NotificationPreferenceRepository;
import com.pricemanagement.repository.SysDictRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTests {

    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private SysDictRepository sysDictRepository;

    @InjectMocks
    private NotificationPreferenceService preferenceService;

    @Test
    void resolveExternalDeliveryDelaysDuringCrossDayQuietHours() {
        NotificationPreference preference = new NotificationPreference();
        preference.setEnabled(true);
        preference.setQuietStartTime(LocalTime.of(22, 0));
        preference.setQuietEndTime(LocalTime.of(8, 0));

        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(1L, NotificationService.TYPE_PRICE_PUBLISHED, NotificationService.CHANNEL_APP_PUSH))
                .thenReturn(Optional.of(preference));

        NotificationPreferenceService.DeliveryPreferenceDecision decision = preferenceService.resolveExternalDelivery(
                1L,
                NotificationService.TYPE_PRICE_PUBLISHED,
                NotificationService.CHANNEL_APP_PUSH,
                NotificationMessage.NotificationPriority.NORMAL,
                LocalDateTime.of(2026, 6, 5, 23, 30));

        assertThat(decision.enabled()).isTrue();
        assertThat(decision.delayed()).isTrue();
        assertThat(decision.nextDeliveryTime()).isEqualTo(LocalDateTime.of(2026, 6, 6, 8, 0));
        assertThat(decision.errorCode()).isEqualTo("QUIET_HOURS");
    }

    @Test
    void urgentNotificationBypassesQuietHoursWithReason() {
        NotificationPreference preference = new NotificationPreference();
        preference.setEnabled(true);
        preference.setQuietStartTime(LocalTime.of(22, 0));
        preference.setQuietEndTime(LocalTime.of(8, 0));

        when(preferenceRepository.findByUserIdAndNotificationTypeAndChannel(1L, NotificationService.TYPE_PRICE_PUBLISHED, NotificationService.CHANNEL_APP_PUSH))
                .thenReturn(Optional.of(preference));

        NotificationPreferenceService.DeliveryPreferenceDecision decision = preferenceService.resolveExternalDelivery(
                1L,
                NotificationService.TYPE_PRICE_PUBLISHED,
                NotificationService.CHANNEL_APP_PUSH,
                NotificationMessage.NotificationPriority.URGENT,
                LocalDateTime.of(2026, 6, 5, 23, 30));

        assertThat(decision.enabled()).isTrue();
        assertThat(decision.delayed()).isFalse();
        assertThat(decision.errorCode()).isEqualTo("QUIET_HOURS_BYPASSED");
    }

    @Test
    void saveRejectsDictionaryValuesThatAreNotActive() {
        NotificationPreferenceDTO dto = new NotificationPreferenceDTO();
        dto.setNotificationType("UNKNOWN_TYPE");
        dto.setChannel(NotificationService.CHANNEL_APP_PUSH);
        dto.setEnabled(true);

        when(sysDictRepository.findByCategoryAndDictKey("notification_type", "UNKNOWN_TYPE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> preferenceService.save(1L, List.of(dto)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通知类型不存在或未启用");
    }

    @Test
    void saveRejectsHalfQuietWindow() {
        NotificationPreferenceDTO dto = new NotificationPreferenceDTO();
        dto.setNotificationType(NotificationService.TYPE_PRICE_PUBLISHED);
        dto.setChannel(NotificationService.CHANNEL_APP_PUSH);
        dto.setQuietStartTime(LocalTime.of(22, 0));

        when(sysDictRepository.findByCategoryAndDictKey("notification_type", NotificationService.TYPE_PRICE_PUBLISHED))
                .thenReturn(Optional.of(activeDict()));
        when(sysDictRepository.findByCategoryAndDictKey("notification_channel", NotificationService.CHANNEL_APP_PUSH))
                .thenReturn(Optional.of(activeDict()));

        assertThatThrownBy(() -> preferenceService.save(1L, List.of(dto)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("免打扰开始和结束时间必须同时填写");
    }

    private SysDict activeDict() {
        SysDict dict = new SysDict();
        dict.setStatus(CommonStatus.ACTIVE);
        return dict;
    }
}
