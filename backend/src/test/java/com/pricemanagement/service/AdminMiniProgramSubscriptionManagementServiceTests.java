package com.pricemanagement.service;

import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMiniProgramEligibility;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMiniProgramResolutionRepository;
import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.NotificationPreferenceRepository;
import com.pricemanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMiniProgramSubscriptionManagementServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    @Mock
    private NotificationMiniProgramResolutionRepository resolutionRepository;
    @Mock
    private NotificationDeliveryLogRepository deliveryLogRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    @Mock
    private NotificationMiniProgramEligibilityService eligibilityService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AdminMiniProgramSubscriptionManagementService service;

    @Test
    void sendGuideToUserSkipsCurrentlySnoozedUser() {
        when(resolutionRepository.findSnoozedUserIds(any(), any())).thenReturn(List.of(1L));

        int sent = service.sendGuideToUser(1L, 99L);

        assertThat(sent).isZero();
        verify(userRepository, never()).findAllById(any());
        verify(notificationService, never()).create(any());
    }

    @Test
    void sendTestDeliveryUsesIsolatedTestDeliveryPath() {
        User user = new User();
        user.setId(1L);
        user.setStatus(com.pricemanagement.constants.CommonStatus.ACTIVE);
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(40L);
        com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template template =
                new com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template();
        template.setTemplateId("template-1");
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(runtimeConfigService.resolveTemplate(NotificationService.TYPE_PRICE_PUBLISHED))
                .thenReturn(java.util.Optional.of(template));
        when(notificationService.createTestDelivery(
                NotificationService.TYPE_PRICE_PUBLISHED,
                user,
                NotificationService.CHANNEL_MINI_PROGRAM,
                99L)).thenReturn(delivery);

        com.pricemanagement.dto.NotificationMiniProgramTestDeliveryRequest request =
                new com.pricemanagement.dto.NotificationMiniProgramTestDeliveryRequest();
        request.setUserId(1L);
        request.setNotificationType(NotificationService.TYPE_PRICE_PUBLISHED);

        assertThat(service.sendTestDelivery(request, 99L)).isEqualTo(40L);
        verify(notificationService).createTestDelivery(
                NotificationService.TYPE_PRICE_PUBLISHED,
                user,
                NotificationService.CHANNEL_MINI_PROGRAM,
                99L);
        verify(notificationService, never()).create(any());
    }

    @Test
    void listWithAggregateStatusUsesDatabasePagination() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(userRepository.findActiveMiniProgramSubscriptionTargetsByEligibilityStatus(
                eq(com.pricemanagement.constants.CommonStatus.ACTIVE),
                eq(NotificationMiniProgramEligibility.RowStatus.LOW_BALANCE),
                eq(User.Role.EDITOR),
                eq("iron"),
                eq(pageable)))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));

        var result = service.list(User.Role.EDITOR, "LOW_BALANCE", "iron", pageable);

        assertThat(result.getTotalElements()).isZero();
        verify(userRepository).findActiveMiniProgramSubscriptionTargetsByEligibilityStatus(
                com.pricemanagement.constants.CommonStatus.ACTIVE,
                NotificationMiniProgramEligibility.RowStatus.LOW_BALANCE,
                User.Role.EDITOR,
                "iron",
                pageable);
        verify(userRepository, never()).findByStatus(any());
    }
}
