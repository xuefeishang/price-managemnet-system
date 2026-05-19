package com.pricemanagement.service;

import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeIdService {

    private final UserRepository userRepository;
    private final Random random = new Random();

    private static final int EMPLOYEE_ID_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 100;

    /**
     * 生成唯一的6位工号
     */
    public String generateEmployeeId() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String employeeId = generateRandomEmployeeId();
            if (!userRepository.existsByEmployeeId(employeeId)) {
                log.debug("Generated employee ID: {}", employeeId);
                return employeeId;
            }
        }
        throw new RuntimeException("无法生成唯一工号，请稍后重试");
    }

    /**
     * 生成随机6位数字工号（不以0开头）
     */
    private String generateRandomEmployeeId() {
        int min = (int) Math.pow(10, EMPLOYEE_ID_LENGTH - 1);
        int max = (int) Math.pow(10, EMPLOYEE_ID_LENGTH) - 1;
        int id = min + random.nextInt(max - min + 1);
        return String.valueOf(id);
    }

    /**
     * 验证工号格式
     */
    public boolean isValidEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.length() != EMPLOYEE_ID_LENGTH) {
            return false;
        }
        return employeeId.matches("\\d{" + EMPLOYEE_ID_LENGTH + "}");
    }
}