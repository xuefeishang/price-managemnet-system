package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationDTO;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationRecipient;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.NotificationRecipientRepository;
import com.pricemanagement.repository.SysDictRepository;
import com.pricemanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationMessageRepository messageRepository;
    @Mock
    private NotificationRecipientRepository recipientRepository;
    @Mock
    private NotificationDeliveryLogRepository deliveryLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SysDictRepository sysDictRepository;
    @Mock
    private NotificationOutboxService notificationOutboxService;
    @Mock
    private NotificationPreferenceService notificationPreferenceService;
    @Mock
    private NotificationThrottleService notificationThrottleService;
    @Mock
    private NotificationRealtimeService notificationRealtimeService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getMyNotificationsUsesPagedRecipientsAndBatchMessageLookup() {
        PageRequest pageable = PageRequest.of(0, 20);
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(10L);
        recipient.setMessageId(20L);
        recipient.setUserId(1L);
        NotificationMessage message = new NotificationMessage();
        message.setId(20L);
        message.setType(NotificationService.TYPE_PRICE_PUBLISHED);
        message.setTitle("价格已更新");
        Page<NotificationRecipient> page = new PageImpl<>(List.of(recipient), pageable, 1);

        when(recipientRepository.findVisibleMyRecipients(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.isNull(),
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(page);
        when(messageRepository.findAllById(List.of(20L))).thenReturn(List.of(message));

        Page<NotificationDTO> result = notificationService.getMyNotifications(1L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getMessageId()).isEqualTo(20L);
        assertThat(result.getContent().getFirst().getTitle()).isEqualTo("价格已更新");
        verify(recipientRepository).findVisibleMyRecipients(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.isNull(),
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(pageable));
        verify(messageRepository).findAllById(List.of(20L));
        verify(messageRepository, never()).findById(20L);
    }

    @Test
    void getAdminDeliveryLogsUsesRepositoryPaginationAndFilters() {
        PageRequest pageable = PageRequest.of(0, 10);
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(40L);
        delivery.setMessageId(20L);
        delivery.setChannel(NotificationService.CHANNEL_MINI_PROGRAM);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.FAILED);
        Page<NotificationDeliveryLog> page = new PageImpl<>(List.of(delivery), pageable, 1);
        when(deliveryLogRepository.findAdminDeliveryLogs(
                20L,
                NotificationService.CHANNEL_MINI_PROGRAM,
                NotificationDeliveryLog.DeliveryStatus.FAILED,
                "WECHAT",
                pageable)).thenReturn(page);

        Page<NotificationDeliveryLog> result = notificationService.getAdminDeliveryLogs(
                20L,
                " MINI_PROGRAM ",
                NotificationDeliveryLog.DeliveryStatus.FAILED,
                " WECHAT ",
                pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(deliveryLogRepository).findAdminDeliveryLogs(
                20L,
                NotificationService.CHANNEL_MINI_PROGRAM,
                NotificationDeliveryLog.DeliveryStatus.FAILED,
                "WECHAT",
                pageable);
    }

    @Test
    void unreadCountUsesVisibleNonArchivedQuery() {
        when(recipientRepository.countVisibleByUserIdAndReadStatus(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(NotificationRecipient.ReadStatus.UNREAD),
                any(LocalDateTime.class)))
                .thenReturn(3L);

        long count = notificationService.getUnreadCount(1L);

        assertThat(count).isEqualTo(3L);
        verify(recipientRepository).countVisibleByUserIdAndReadStatus(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(NotificationRecipient.ReadStatus.UNREAD),
                any(LocalDateTime.class));
    }

    @Test
    void archiveMessageForAllArchivesRecipientsByMessageId() {
        when(recipientRepository.archiveByMessageId(20L)).thenReturn(2);

        int count = notificationService.archiveMessageForAll(20L);

        assertThat(count).isEqualTo(2);
        verify(recipientRepository).archiveByMessageId(20L);
    }

    @Test
    void archiveMarksOnlyCurrentUsersRecipientArchived() {
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);
        recipient.setMessageId(20L);
        recipient.setUserId(1L);
        recipient.setArchived(false);

        when(recipientRepository.findByMessageIdAndUserId(20L, 1L)).thenReturn(Optional.of(recipient));

        notificationService.archive(20L, 1L);

        assertThat(recipient.getArchived()).isTrue();
        assertThat(recipient.getArchivedTime()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(recipientRepository).save(recipient);
    }

    @Test
    void createMessageDelaysExternalDeliveryDuringQuietHours() {
        stubActiveDicts();
        User user = new User();
        user.setId(1L);

        NotificationMessage savedMessage = new NotificationMessage();
        savedMessage.setId(20L);
        savedMessage.setType(NotificationService.TYPE_PRICE_PUBLISHED);
        savedMessage.setPriority(NotificationMessage.NotificationPriority.NORMAL);
        when(messageRepository.save(any(NotificationMessage.class))).thenReturn(savedMessage);

        NotificationRecipient savedRecipient = new NotificationRecipient();
        savedRecipient.setId(30L);
        savedRecipient.setMessageId(20L);
        savedRecipient.setUserId(1L);
        when(recipientRepository.save(any(NotificationRecipient.class))).thenReturn(savedRecipient);

        when(deliveryLogRepository.save(any(NotificationDeliveryLog.class))).thenAnswer(invocation -> {
            NotificationDeliveryLog delivery = invocation.getArgument(0);
            delivery.setId(40L);
            return delivery;
        });

        LocalDateTime delayedUntil = LocalDateTime.now().plusHours(2);
        when(notificationPreferenceService.resolveExternalDelivery(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(NotificationService.TYPE_PRICE_PUBLISHED),
                org.mockito.ArgumentMatchers.eq(NotificationService.CHANNEL_APP_PUSH),
                org.mockito.ArgumentMatchers.eq(NotificationMessage.NotificationPriority.NORMAL),
                any(LocalDateTime.class)))
                .thenReturn(new NotificationPreferenceService.DeliveryPreferenceDecision(
                        true,
                        true,
                        delayedUntil,
                        "QUIET_HOURS",
                        "当前处于免打扰时段，外部通知将延迟投递"));

        notificationService.createMessage(
                NotificationService.TYPE_PRICE_PUBLISHED,
                "价格已更新",
                "摘要",
                "正文",
                "PRICE",
                1L,
                List.of(NotificationService.CHANNEL_APP_PUSH),
                List.of(user),
                1L,
                NotificationMessage.NotificationPriority.NORMAL,
                null,
                null,
                null);

        verify(notificationOutboxService).enqueueDelivery(any(NotificationDeliveryLog.class), org.mockito.ArgumentMatchers.eq(delayedUntil));
    }

    @Test
    void createMessageAlwaysKeepsInAppChannelWhenOnlyExternalChannelIsRequested() {
        stubActiveDicts();
        User user = new User();
        user.setId(1L);

        when(messageRepository.save(any(NotificationMessage.class))).thenAnswer(invocation -> {
            NotificationMessage message = invocation.getArgument(0);
            message.setId(20L);
            return message;
        });
        when(recipientRepository.save(any(NotificationRecipient.class))).thenAnswer(invocation -> {
            NotificationRecipient recipient = invocation.getArgument(0);
            recipient.setId(30L);
            return recipient;
        });
        when(deliveryLogRepository.save(any(NotificationDeliveryLog.class))).thenAnswer(invocation -> {
            NotificationDeliveryLog delivery = invocation.getArgument(0);
            delivery.setId(40L);
            return delivery;
        });
        when(notificationPreferenceService.resolveExternalDelivery(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(NotificationService.TYPE_PRICE_PUBLISHED),
                org.mockito.ArgumentMatchers.eq(NotificationService.CHANNEL_APP_PUSH),
                org.mockito.ArgumentMatchers.eq(NotificationMessage.NotificationPriority.NORMAL),
                any(LocalDateTime.class)))
                .thenReturn(new NotificationPreferenceService.DeliveryPreferenceDecision(
                        true,
                        false,
                        null,
                        null,
                        null));

        notificationService.createMessage(
                NotificationService.TYPE_PRICE_PUBLISHED,
                "价格已更新",
                "摘要",
                "正文",
                "PRICE",
                1L,
                List.of(NotificationService.CHANNEL_APP_PUSH),
                List.of(user),
                1L,
                NotificationMessage.NotificationPriority.NORMAL,
                null,
                null,
                null);

        verify(messageRepository).save(org.mockito.ArgumentMatchers.argThat(message ->
                message.getChannels().contains(NotificationService.CHANNEL_IN_APP)
                        && message.getChannels().contains(NotificationService.CHANNEL_APP_PUSH)));
        verify(deliveryLogRepository).save(org.mockito.ArgumentMatchers.argThat(delivery ->
                NotificationService.CHANNEL_IN_APP.equals(delivery.getChannel())
                        && delivery.getStatus() == NotificationDeliveryLog.DeliveryStatus.SUCCESS));
        verify(deliveryLogRepository).save(org.mockito.ArgumentMatchers.argThat(delivery ->
                NotificationService.CHANNEL_APP_PUSH.equals(delivery.getChannel())
                        && delivery.getStatus() == NotificationDeliveryLog.DeliveryStatus.PENDING));
        verify(notificationOutboxService).enqueueDelivery(
                org.mockito.ArgumentMatchers.argThat(delivery -> NotificationService.CHANNEL_APP_PUSH.equals(delivery.getChannel())),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void createTestDeliveryIsArchivedAndDoesNotCreateInAppDelivery() {
        stubActiveDicts();
        User user = new User();
        user.setId(1L);

        when(messageRepository.save(any(NotificationMessage.class))).thenAnswer(invocation -> {
            NotificationMessage message = invocation.getArgument(0);
            message.setId(20L);
            return message;
        });
        when(recipientRepository.save(any(NotificationRecipient.class))).thenAnswer(invocation -> {
            NotificationRecipient recipient = invocation.getArgument(0);
            recipient.setId(30L);
            return recipient;
        });
        when(deliveryLogRepository.save(any(NotificationDeliveryLog.class))).thenAnswer(invocation -> {
            NotificationDeliveryLog delivery = invocation.getArgument(0);
            delivery.setId(40L);
            return delivery;
        });

        NotificationDeliveryLog result = notificationService.createTestDelivery(
                NotificationService.TYPE_PRICE_PUBLISHED,
                user,
                NotificationService.CHANNEL_MINI_PROGRAM,
                99L);

        assertThat(result.getTest()).isTrue();
        assertThat(result.getChannel()).isEqualTo(NotificationService.CHANNEL_MINI_PROGRAM);
        verify(messageRepository).save(org.mockito.ArgumentMatchers.argThat(message ->
                "NOTIFICATION_TEST".equals(message.getBusinessType())
                        && !message.getChannels().contains(NotificationService.CHANNEL_IN_APP)));
        verify(recipientRepository).save(org.mockito.ArgumentMatchers.argThat(recipient ->
                Boolean.TRUE.equals(recipient.getArchived()) && recipient.getArchivedTime() != null));
        verify(deliveryLogRepository, never()).save(org.mockito.ArgumentMatchers.argThat(delivery ->
                NotificationService.CHANNEL_IN_APP.equals(delivery.getChannel())));
        verify(notificationOutboxService).enqueueDelivery(
                org.mockito.ArgumentMatchers.eq(result),
                any(LocalDateTime.class));
    }

    @Test
    void createMessageAppliesFrequencyAggregationWhenRuleIsTriggered() {
        stubActiveDicts();
        User user = new User();
        user.setId(1L);

        NotificationMessage savedMessage = new NotificationMessage();
        savedMessage.setId(20L);
        savedMessage.setPriority(NotificationMessage.NotificationPriority.NORMAL);
        when(messageRepository.save(any(NotificationMessage.class))).thenAnswer(invocation -> {
            NotificationMessage message = invocation.getArgument(0);
            message.setId(20L);
            return message;
        });

        NotificationRecipient savedRecipient = new NotificationRecipient();
        savedRecipient.setId(30L);
        savedRecipient.setMessageId(20L);
        savedRecipient.setUserId(1L);
        when(recipientRepository.save(any(NotificationRecipient.class))).thenReturn(savedRecipient);

        when(deliveryLogRepository.save(any(NotificationDeliveryLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SysDict typeDict = new SysDict();
        typeDict.setCategory("notification_type");
        typeDict.setDictKey("TASK_FAILED");
        typeDict.setDictValue("任务失败");
        typeDict.setStatus(CommonStatus.ACTIVE);
        when(sysDictRepository.findByCategoryAndDictKey("notification_type", "TASK_FAILED"))
                .thenReturn(Optional.of(typeDict));
        when(notificationThrottleService.shouldAggregate(
                org.mockito.ArgumentMatchers.eq("TASK_FAILED"),
                org.mockito.ArgumentMatchers.eq(NotificationMessage.NotificationPriority.NORMAL)))
                .thenReturn(true);
        when(notificationThrottleService.aggregationSummary("TASK_FAILED"))
                .thenReturn("已在30分钟内产生6条同类通知，按频控规则聚合。");
        when(notificationThrottleService.aggregationDedupeKey("TASK_FAILED"))
                .thenReturn("NOTIFICATION_AGGREGATE:TASK_FAILED:30:1");

        notificationService.createMessage(
                "TASK_FAILED",
                "任务失败",
                "摘要",
                "正文",
                "TASK",
                1L,
                List.of(NotificationService.CHANNEL_IN_APP),
                List.of(user),
                1L,
                NotificationMessage.NotificationPriority.NORMAL,
                null,
                null,
                null);

        verify(messageRepository).save(org.mockito.ArgumentMatchers.argThat(message ->
                "任务失败聚合提醒".equals(message.getTitle())
                        && "NOTIFICATION_AGGREGATE:TASK_FAILED:30:1".equals(message.getDedupeKey())
                        && message.getContent().contains("最新事件：正文")));
    }

    @Test
    void createMessageAggregatesEvenWhenBusinessDedupeKeyExists() {
        stubActiveDicts();
        User user = new User();
        user.setId(1L);

        NotificationMessage existing = new NotificationMessage();
        existing.setId(20L);
        existing.setType("TASK_FAILED");
        existing.setDedupeKey("NOTIFICATION_AGGREGATE:TASK_FAILED:30:1");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);
        recipient.setMessageId(20L);
        recipient.setUserId(1L);
        recipient.setReadStatus(NotificationRecipient.ReadStatus.READ);
        recipient.setArchived(true);

        SysDict typeDict = new SysDict();
        typeDict.setCategory("notification_type");
        typeDict.setDictKey("TASK_FAILED");
        typeDict.setDictValue("任务失败");
        typeDict.setStatus(CommonStatus.ACTIVE);
        when(sysDictRepository.findByCategoryAndDictKey("notification_type", "TASK_FAILED"))
                .thenReturn(Optional.of(typeDict));
        when(notificationThrottleService.shouldAggregate(
                org.mockito.ArgumentMatchers.eq("TASK_FAILED"),
                org.mockito.ArgumentMatchers.eq(NotificationMessage.NotificationPriority.HIGH)))
                .thenReturn(true);
        when(notificationThrottleService.aggregationSummary("TASK_FAILED"))
                .thenReturn("已在30分钟内产生6条同类通知，按频控规则聚合。");
        when(notificationThrottleService.aggregationDedupeKey("TASK_FAILED"))
                .thenReturn("NOTIFICATION_AGGREGATE:TASK_FAILED:30:1");
        when(messageRepository.findByDedupeKey("NOTIFICATION_AGGREGATE:TASK_FAILED:30:1"))
                .thenReturn(Optional.of(existing));
        when(messageRepository.save(any(NotificationMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientRepository.findByMessageIdAndUserId(20L, 1L)).thenReturn(Optional.of(recipient));
        when(recipientRepository.save(any(NotificationRecipient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deliveryLogRepository.save(any(NotificationDeliveryLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientRepository.countVisibleByUserIdAndReadStatus(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(NotificationRecipient.ReadStatus.UNREAD),
                any(LocalDateTime.class)))
                .thenReturn(1L);

        NotificationMessage result = notificationService.createMessage(
                "TASK_FAILED",
                "任务失败",
                "摘要",
                "新失败事件",
                "TASK",
                1L,
                List.of(NotificationService.CHANNEL_IN_APP),
                List.of(user),
                1L,
                NotificationMessage.NotificationPriority.HIGH,
                null,
                null,
                "TASK_FAILED:123");

        assertThat(result.getDedupeKey()).isEqualTo("NOTIFICATION_AGGREGATE:TASK_FAILED:30:1");
        assertThat(result.getContent()).contains("最新事件：新失败事件");
        assertThat(recipient.getReadStatus()).isEqualTo(NotificationRecipient.ReadStatus.UNREAD);
        assertThat(recipient.getArchived()).isFalse();
        assertThat(result.getEventCount()).isEqualTo(2L);
        verify(messageRepository, never()).findByDedupeKey("TASK_FAILED:123");
        verify(deliveryLogRepository).save(org.mockito.ArgumentMatchers.argThat(delivery ->
                NotificationService.CHANNEL_IN_APP.equals(delivery.getChannel())
                        && delivery.getMessageId().equals(20L)
                        && delivery.getRecipientId().equals(30L)));
        verify(notificationRealtimeService).publishNewNotification(1L, 20L, "TASK_FAILED", 1L);
    }

    private void stubActiveDicts() {
        when(sysDictRepository.findByCategoryAndDictKey(any(String.class), any(String.class)))
                .thenAnswer(invocation -> {
                    SysDict dict = new SysDict();
                    dict.setCategory(invocation.getArgument(0));
                    dict.setDictKey(invocation.getArgument(1));
                    dict.setStatus(CommonStatus.ACTIVE);
                    return Optional.of(dict);
                });
    }
}
