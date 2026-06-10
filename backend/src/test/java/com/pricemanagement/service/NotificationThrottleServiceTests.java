package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.SysDictRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationThrottleServiceTests {

    @Mock
    private SysDictRepository sysDictRepository;
    @Mock
    private NotificationMessageRepository messageRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private NotificationThrottleService throttleService;

    @Test
    void aggregationUsesMessageEventCountAndIncludesCurrentEventInSummary() {
        SysDict rule = new SysDict();
        rule.setStatus(CommonStatus.ACTIVE);
        rule.setExtraValue("{\"enabled\":true,\"windowMinutes\":30,\"maxCount\":5}");
        when(sysDictRepository.findByCategoryAndDictKey("notification_frequency_rule", "TASK_FAILED"))
                .thenReturn(Optional.of(rule));
        when(messageRepository.sumEventCountByTypeAfter(
                org.mockito.ArgumentMatchers.eq("TASK_FAILED"),
                any(LocalDateTime.class))).thenReturn(7L);

        assertThat(throttleService.shouldAggregate("TASK_FAILED", NotificationMessage.NotificationPriority.HIGH))
                .isTrue();
        assertThat(throttleService.aggregationSummary("TASK_FAILED"))
                .contains("产生8条同类通知");
    }
}
