package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.dto.SystemNoticeCreateRequest;
import com.pricemanagement.entity.SystemNotice;
import com.pricemanagement.repository.SystemNoticeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemNoticeServiceTests {

    @Mock
    private SystemNoticeRepository noticeRepository;
    @Mock
    private NotificationService notificationService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SystemNoticeService systemNoticeService;

    @Test
    void cancelArchivesPublishedNotificationMessage() {
        SystemNotice notice = new SystemNotice();
        notice.setId(1L);
        notice.setStatus(SystemNotice.NoticeStatus.PUBLISHED);
        notice.setNotificationMessageId(20L);

        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(noticeRepository.save(notice)).thenReturn(notice);

        SystemNotice result = systemNoticeService.cancel(1L);

        assertThat(result.getStatus()).isEqualTo(SystemNotice.NoticeStatus.CANCELLED);
        assertThat(result.getCancelledTime()).isNotNull();
        verify(notificationService).archiveMessageForAll(20L);
    }

    @Test
    void createRejectsExpireTimeBeforeScheduledPublishTime() {
        SystemNoticeCreateRequest request = new SystemNoticeCreateRequest();
        request.setTitle("公告");
        request.setContent("正文");
        request.setScheduledPublishTime(LocalDateTime.now().plusDays(2));
        request.setExpireTime(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> systemNoticeService.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("公告过期时间必须晚于计划发布时间");
    }
}
