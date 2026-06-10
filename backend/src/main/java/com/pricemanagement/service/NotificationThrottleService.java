package com.pricemanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationThrottleRuleDTO;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationThrottleService {

    private static final String CATEGORY = "notification_frequency_rule";

    private final SysDictRepository sysDictRepository;
    private final NotificationMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<NotificationThrottleRuleDTO> listRules() {
        List<NotificationThrottleRuleDTO> rules = new ArrayList<>();
        sysDictRepository.findByCategoryAndStatusOrderBySortOrderAsc(CATEGORY, CommonStatus.ACTIVE)
                .forEach(dict -> rules.add(toRule(dict.getDictKey(), dict.getExtraValue())));
        return rules.stream()
                .sorted(Comparator.comparing(NotificationThrottleRuleDTO::getType))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean shouldAggregate(String type, NotificationMessage.NotificationPriority priority) {
        if (priority == NotificationMessage.NotificationPriority.URGENT) {
            return false;
        }
        NotificationThrottleRuleDTO rule = findRule(type);
        return rule != null && rule.isEnabled() && rule.isThrottled();
    }

    @Transactional(readOnly = true)
    public String aggregationSummary(String type) {
        NotificationThrottleRuleDTO rule = findRule(type);
        if (rule == null) {
            return null;
        }
        return "已在" + rule.getWindowMinutes() + "分钟内产生" + (rule.getCurrentCount() + 1)
                + "条同类通知，按频控规则聚合。";
    }

    @Transactional(readOnly = true)
    public String aggregationDedupeKey(String type) {
        NotificationThrottleRuleDTO rule = findRule(type);
        int windowMinutes = rule == null ? 30 : Math.max(rule.getWindowMinutes(), 1);
        long bucket = System.currentTimeMillis() / (windowMinutes * 60_000L);
        return "NOTIFICATION_AGGREGATE:" + type + ":" + windowMinutes + ":" + bucket;
    }

    private NotificationThrottleRuleDTO findRule(String type) {
        return sysDictRepository.findByCategoryAndDictKey(CATEGORY, type)
                .filter(dict -> dict.getStatus() == CommonStatus.ACTIVE)
                .map(dict -> toRule(type, dict.getExtraValue()))
                .orElse(null);
    }

    private NotificationThrottleRuleDTO toRule(String type, String extraValue) {
        NotificationThrottleRuleDTO rule = new NotificationThrottleRuleDTO();
        rule.setType(type);
        rule.setEnabled(true);
        rule.setWindowMinutes(30);
        rule.setMaxCount(5);
        if (extraValue != null && !extraValue.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(extraValue);
                if (node.has("enabled")) {
                    rule.setEnabled(node.get("enabled").asBoolean(true));
                }
                if (node.has("windowMinutes")) {
                    rule.setWindowMinutes(Math.max(node.get("windowMinutes").asInt(30), 1));
                }
                if (node.has("maxCount")) {
                    rule.setMaxCount(Math.max(node.get("maxCount").asInt(5), 1));
                }
            } catch (Exception ignored) {
                // Bad rule config falls back to conservative defaults.
            }
        }
        long currentCount = messageRepository.sumEventCountByTypeAfter(
                type,
                LocalDateTime.now().minusMinutes(rule.getWindowMinutes()));
        rule.setCurrentCount(currentCount);
        rule.setThrottled(rule.isEnabled() && currentCount >= rule.getMaxCount());
        return rule;
    }
}
