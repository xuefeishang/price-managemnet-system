package com.pricemanagement.service;

import com.pricemanagement.entity.LoginHistory;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.LoginHistoryRepository;
import com.pricemanagement.util.IpAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginHistoryService {

    private final LoginHistoryRepository loginHistoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(User user, HttpServletRequest request) {
        LoginHistory history = base(user.getId(), user.getUsername(), request);
        history.setResult("SUCCESS");
        loginHistoryRepository.save(history);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String username, User user, HttpServletRequest request, String reason) {
        LoginHistory history = base(user == null ? null : user.getId(),
                user == null ? username : user.getUsername(), request);
        history.setResult("FAILED");
        history.setFailureReason(truncate(reason, 500));
        loginHistoryRepository.save(history);
    }

    private LoginHistory base(Long userId, String username, HttpServletRequest request) {
        LoginHistory history = new LoginHistory();
        history.setUserId(userId);
        history.setUsername(username);
        history.setLoginTime(LocalDateTime.now());
        history.setIpAddress(IpAddressUtil.getClientIp(request));
        history.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        return history;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

