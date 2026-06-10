package com.pricemanagement.config;

import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.util.OperationLogHelper;
import com.pricemanagement.util.SensitiveDataMasker;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogHelper operationLogHelper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        String requestParams = operationLog.logParams() ? buildRequestParams(joinPoint.getArgs()) : null;
        try {
            Object result = joinPoint.proceed();
            operationLogHelper.logSuccess(
                    operationLog.module(),
                    operationLog.type(),
                    operationLog.description(),
                    requestParams
            );
            return result;
        } catch (Throwable throwable) {
            operationLogHelper.logError(
                    operationLog.module(),
                    operationLog.type(),
                    operationLog.description() + "失败",
                    requestParams,
                    throwable.getMessage()
            );
            throw throwable;
        }
    }

    private String buildRequestParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            return SensitiveDataMasker.mask(Arrays.stream(args)
                    .filter(arg -> arg != null)
                    .filter(arg -> !(arg instanceof ServletRequest))
                    .filter(arg -> !(arg instanceof ServletResponse))
                    .filter(arg -> !(arg instanceof MultipartFile))
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")));
        } catch (Exception e) {
            log.debug("Could not build operation log params: {}", e.getMessage());
            return null;
        }
    }
}
