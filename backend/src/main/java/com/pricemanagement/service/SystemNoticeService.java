package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.dto.NotificationCreateCommand;
import com.pricemanagement.dto.SystemNoticeCreateRequest;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.SystemNotice;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.SystemNoticeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemNoticeService {

    public static final String TYPE_SYSTEM_NOTICE = NotificationService.TYPE_SYSTEM_NOTICE;

    private final SystemNoticeRepository noticeRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<SystemNotice> list(SystemNotice.NoticeStatus status, Pageable pageable) {
        Specification<SystemNotice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return noticeRepository.findAll(spec, pageable);
    }

    @Transactional
    public SystemNotice create(SystemNoticeCreateRequest request, Long createdBy) {
        validate(request);
        SystemNotice notice = new SystemNotice();
        notice.setTitle(request.getTitle().trim());
        notice.setSummary(normalize(request.getSummary()));
        notice.setContent(request.getContent().trim());
        notice.setTargetRoles(toJson(request.getTargetRoles() == null || request.getTargetRoles().isEmpty()
                ? List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER)
                : request.getTargetRoles()));
        notice.setChannels(toJson(request.getChannels() == null || request.getChannels().isEmpty()
                ? List.of(NotificationService.CHANNEL_IN_APP)
                : request.getChannels()));
        notice.setPriority(request.getPriority() == null
                ? NotificationMessage.NotificationPriority.NORMAL
                : request.getPriority());
        notice.setScheduledPublishTime(request.getScheduledPublishTime());
        notice.setExpireTime(request.getExpireTime());
        notice.setCreatedBy(createdBy);
        notice.setStatus(request.getScheduledPublishTime() != null
                && request.getScheduledPublishTime().isAfter(LocalDateTime.now())
                ? SystemNotice.NoticeStatus.SCHEDULED
                : SystemNotice.NoticeStatus.DRAFT);
        return noticeRepository.save(notice);
    }

    @Transactional
    public SystemNotice publish(Long id) {
        SystemNotice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("系统公告不存在"));
        if (notice.getStatus() == SystemNotice.NoticeStatus.PUBLISHED) {
            return notice;
        }
        if (notice.getStatus() == SystemNotice.NoticeStatus.CANCELLED) {
            throw new IllegalStateException("已撤回公告不能发布");
        }
        if (notice.getExpireTime() != null && !notice.getExpireTime().isAfter(LocalDateTime.now())) {
            notice.setStatus(SystemNotice.NoticeStatus.EXPIRED);
            return noticeRepository.save(notice);
        }

        NotificationCreateCommand command = new NotificationCreateCommand();
        command.setEventType(TYPE_SYSTEM_NOTICE);
        command.setTitle(notice.getTitle());
        command.setSummary(notice.getSummary() == null || notice.getSummary().isBlank()
                ? notice.getTitle()
                : notice.getSummary());
        command.setContent(notice.getContent());
        command.setBusinessType("SYSTEM");
        command.setBusinessId(notice.getId());
        command.setRecipientRoles(readRoles(notice.getTargetRoles()));
        command.setChannels(readStringList(notice.getChannels()));
        command.setPriority(notice.getPriority());
        command.setLinkType(NotificationService.LINK_TYPE_SYSTEM_NOTICE);
        command.setLinkParams(toJson(Map.of("noticeId", notice.getId())));
        command.setDedupeKey("SYSTEM_NOTICE:" + notice.getId());
        command.setExpireTime(notice.getExpireTime());
        command.setCreatedBy(notice.getCreatedBy());

        NotificationMessage message = notificationService.create(command);
        notice.setNotificationMessageId(message.getId());
        notice.setPublishedTime(LocalDateTime.now());
        notice.setStatus(SystemNotice.NoticeStatus.PUBLISHED);
        return noticeRepository.save(notice);
    }

    @Transactional
    public SystemNotice cancel(Long id) {
        SystemNotice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("系统公告不存在"));
        if (notice.getStatus() == SystemNotice.NoticeStatus.EXPIRED) {
            throw new IllegalStateException("已过期公告不能撤回");
        }
        notice.setStatus(SystemNotice.NoticeStatus.CANCELLED);
        notice.setCancelledTime(LocalDateTime.now());
        notificationService.archiveMessageForAll(notice.getNotificationMessageId());
        return noticeRepository.save(notice);
    }

    @Scheduled(fixedDelayString = "${notification.notice.poll-delay-ms:60000}")
    @Transactional
    public void publishDueAndExpire() {
        LocalDateTime now = LocalDateTime.now();
        for (SystemNotice notice : noticeRepository.findByStatusAndScheduledPublishTimeLessThanEqual(
                SystemNotice.NoticeStatus.SCHEDULED, now)) {
            publish(notice.getId());
        }
        for (SystemNotice notice : noticeRepository.findByStatusAndExpireTimeLessThanEqual(
                SystemNotice.NoticeStatus.PUBLISHED, now)) {
            notice.setStatus(SystemNotice.NoticeStatus.EXPIRED);
            notificationService.archiveMessageForAll(notice.getNotificationMessageId());
            noticeRepository.save(notice);
        }
    }

    private void validate(SystemNoticeCreateRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("公告标题不能为空");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("公告内容不能为空");
        }
        if (request.getExpireTime() != null && !request.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("公告过期时间必须晚于当前时间");
        }
        if (request.getScheduledPublishTime() != null && request.getExpireTime() != null
                && !request.getExpireTime().isAfter(request.getScheduledPublishTime())) {
            throw new IllegalArgumentException("公告过期时间必须晚于计划发布时间");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<User.Role> readRoles(String json) {
        try {
            return objectMapper.readerForListOf(User.Role.class).readValue(json);
        } catch (Exception e) {
            return List.of(User.Role.ADMIN);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json);
        } catch (Exception e) {
            return List.of(NotificationService.CHANNEL_IN_APP);
        }
    }
}
