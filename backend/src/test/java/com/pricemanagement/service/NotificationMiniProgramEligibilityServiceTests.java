package com.pricemanagement.service;

import com.pricemanagement.entity.NotificationMiniProgramEligibility;
import com.pricemanagement.entity.NotificationMiniProgramSubscription;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationMiniProgramEligibilityRepository;
import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMiniProgramEligibilityServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    @Mock
    private NotificationMiniProgramEligibilityRepository eligibilityRepository;
    @Mock
    private NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationMiniProgramEligibilityService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationMiniProgramEligibilityService(
                userRepository,
                subscriptionRepository,
                eligibilityRepository,
                runtimeConfigService,
                eventPublisher);
        user = new User();
        user.setId(1L);
        user.setWechatOpenid("openid-1");
    }

    @Test
    void evaluatesNormalWhenAuthorizedBalanceIsAboveThreshold() {
        stubTemplates();
        NotificationMiniProgramSubscription subscription = subscription(
                NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT, 2);

        var result = service.evaluate(user, List.of(subscription));

        assertThat(result.rowStatus()).isEqualTo(NotificationMiniProgramEligibility.RowStatus.NORMAL);
        assertThat(result.availableTotal()).isEqualTo(2);
    }

    @Test
    void evaluatesLowBalanceWhenAuthorizedBalanceReachesThreshold() {
        stubTemplates();
        NotificationMiniProgramSubscription subscription = subscription(
                NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT, 1);

        assertThat(service.evaluate(user, List.of(subscription)).rowStatus())
                .isEqualTo(NotificationMiniProgramEligibility.RowStatus.LOW_BALANCE);
    }

    @Test
    void evaluatesUnboundBeforeAuthorizationState() {
        stubTemplates();
        user.setWechatOpenid(null);
        NotificationMiniProgramSubscription subscription = subscription(
                NotificationMiniProgramSubscription.SubscriptionStatus.REJECT, 0);

        assertThat(service.evaluate(user, List.of(subscription)).rowStatus())
                .isEqualTo(NotificationMiniProgramEligibility.RowStatus.UNBOUND);
    }

    @Test
    void evaluatesRejectedWhenConfiguredTemplateIsRejected() {
        stubTemplates();
        NotificationMiniProgramSubscription subscription = subscription(
                NotificationMiniProgramSubscription.SubscriptionStatus.REJECT, 0);

        assertThat(service.evaluate(user, List.of(subscription)).rowStatus())
                .isEqualTo(NotificationMiniProgramEligibility.RowStatus.REJECTED);
    }

    @Test
    void requestRefreshPublishesAfterCommitRefreshEvent() {
        service.requestRefresh(1L);

        verify(eventPublisher).publishEvent(
                new NotificationMiniProgramEligibilityService.UserEligibilityRefreshRequested(1L));
    }

    private NotificationMiniProgramSubscription subscription(
            NotificationMiniProgramSubscription.SubscriptionStatus status,
            int availableCount) {
        NotificationMiniProgramSubscription subscription = new NotificationMiniProgramSubscription();
        subscription.setUserId(1L);
        subscription.setNotificationType("PRICE_PUBLISHED");
        subscription.setTemplateId("template-1");
        subscription.setStatus(status);
        subscription.setAvailableCount(availableCount);
        return subscription;
    }

    private void stubTemplates() {
        when(runtimeConfigService.configuredTemplates()).thenReturn(List.of(
                new NotificationMiniProgramRuntimeConfigService.TemplateConfig("PRICE_PUBLISHED", "template-1")));
    }
}
