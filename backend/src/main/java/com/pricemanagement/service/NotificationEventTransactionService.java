package com.pricemanagement.service;

import com.pricemanagement.dto.NotificationCreateCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationEventTransactionService {

    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(NotificationCreateCommand command) {
        notificationService.create(command);
    }
}
