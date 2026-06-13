package com.pricemanagement.service;

import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMiniProgramSubscriptionServiceTests {

    @Mock
    private NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    @Mock
    private NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationMiniProgramEligibilityService eligibilityService;

    @InjectMocks
    private NotificationMiniProgramSubscriptionService service;

    @Test
    void consumeUsesConditionalAtomicUpdate() {
        when(subscriptionRepository.consumeOne(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1"))
                .thenReturn(1);

        boolean consumed = service.consume(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1");

        assertThat(consumed).isTrue();
        verify(eligibilityService).requestRefresh(1L);
    }

    @Test
    void consumeDoesNotRefreshEligibilityWhenNoBalanceWasUpdated() {
        when(subscriptionRepository.consumeOne(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1"))
                .thenReturn(0);

        boolean consumed = service.consume(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1");

        assertThat(consumed).isFalse();
        verify(eligibilityService, never()).requestRefresh(1L);
    }

    @Test
    void releaseConsumedRestoresBalanceAndRefreshesEligibility() {
        when(subscriptionRepository.releaseOne(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1"))
                .thenReturn(1);

        service.releaseConsumed(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1");

        verify(eligibilityService).requestRefresh(1L);
    }

    @Test
    void markUnauthorizedClearsLocalAuthorizationState() {
        when(subscriptionRepository.markRejected(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1"))
                .thenReturn(1);

        service.markUnauthorized(1L, NotificationService.TYPE_PRICE_PUBLISHED, "template-1", true);

        verify(eligibilityService).requestRefresh(1L);
    }
}
