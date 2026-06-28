package com.pricemanagement.util;

import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.service.ClientIpResolver;
import com.pricemanagement.service.OperationLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogHelperTests {

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private ClientIpResolver clientIpResolver;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsConflictCodeAndMasksPassword() {
        OperationLogHelper helper = new OperationLogHelper(operationLogService, clientIpResolver);

        helper.logError(
                "用户管理",
                OperationLog.OperationType.CREATE,
                "创建用户失败",
                "username=test,password=Password123",
                "用户名已存在，请更换后重试",
                "409");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).log(captor.capture());

        OperationLog log = captor.getValue();
        assertEquals("409", log.getResponseCode());
        assertFalse(log.getRequestParams().contains("Password123"));
        assertEquals("用户名已存在，请更换后重试", log.getErrorMessage());
    }

    @Test
    void masksSuccessLogAndRecordsExecutionTime() {
        OperationLogHelper helper = new OperationLogHelper(operationLogService, clientIpResolver);

        helper.logSuccess("用户管理", OperationLog.OperationType.IMPORT, "批量导入用户",
                "username=test,password=Password123", 25L);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).log(captor.capture());

        OperationLog log = captor.getValue();
        assertEquals("200", log.getResponseCode());
        assertEquals(25L, log.getExecutionTime());
        assertFalse(log.getRequestParams().contains("Password123"));
    }

    @Test
    void recordsIpFromUnifiedResolver() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
        request.setRemoteAddr("198.51.100.9");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(clientIpResolver.resolve(any())).thenReturn("198.51.100.9");
        OperationLogHelper helper = new OperationLogHelper(operationLogService, clientIpResolver);

        helper.logSuccess("用户管理", OperationLog.OperationType.CREATE, "创建用户");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogService).log(captor.capture());

        assertEquals("198.51.100.9", captor.getValue().getIpAddress());
    }
}
