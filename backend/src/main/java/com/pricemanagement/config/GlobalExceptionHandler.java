package com.pricemanagement.config;

import com.pricemanagement.dto.Result;
import com.pricemanagement.exception.RateLimitException;
import com.pricemanagement.exception.TokenRefreshException;
import com.pricemanagement.exception.UserConflictException;
import com.pricemanagement.exception.UserImportValidationException;
import com.pricemanagement.dto.UserImportResult;
import com.pricemanagement.util.DataIntegrityViolationDiagnostics;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Validation failed: {}", errors);
        return new Result<>(400, "参数校验失败", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Request body cannot be read: type={}", ex.getClass().getSimpleName());
        return Result.error(400, "请求字段或格式不正确");
    }

    /**
     * 处理IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return Result.error(400, ex.getMessage());
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return Result.error(401, "认证失败：" + ex.getMessage());
    }

    /**
     * 处理BadCredentialsException
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * 处理访问拒绝异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return Result.error(403, "权限不足，拒绝访问");
    }

    /**
     * 处理限流异常
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Result<Void> handleRateLimitException(RateLimitException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return Result.error(429, ex.getMessage());
    }

    /**
     * 处理 Token 刷新异常
     */
    @ExceptionHandler(TokenRefreshException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleTokenRefreshException(TokenRefreshException ex) {
        log.warn("Token refresh failed: {}", ex.getMessage());
        return Result.error(401, ex.getMessage());
    }

    /**
     * 处理资源不存在异常
     */
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleEmptyResultDataAccessException(org.springframework.dao.EmptyResultDataAccessException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return Result.error(404, "请求的资源不存在");
    }

    /**
     * 处理用户唯一字段或角色关联冲突
     */
    @ExceptionHandler(UserConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleUserConflictException(UserConflictException ex) {
        log.warn("User conflict: reason={}", ex.getReason());
        return Result.error(409, ex.getMessage());
    }

    @ExceptionHandler(UserImportValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<UserImportResult> handleUserImportValidationException(UserImportValidationException ex) {
        log.warn("User import validation failed: totalRows={}, errors={}",
                ex.getResult().totalRows(), ex.getResult().errors().size());
        return new Result<>(400, ex.getMessage(), ex.getResult());
    }

    /**
     * 处理数据完整性异常（如唯一约束冲突）
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDataIntegrityViolationException(org.springframework.dao.DataIntegrityViolationException ex) {
        DataIntegrityViolationDiagnostics.Diagnostic diagnostic = DataIntegrityViolationDiagnostics.inspect(ex);
        log.warn("Data integrity violation: constraint={}, rootType={}",
                diagnostic.constraintName(), diagnostic.rootExceptionType());
        return Result.error(409, "数据操作失败，可能违反数据完整性约束");
    }

    /**
     * 处理 SSE 等异步请求的正常生命周期结束：空闲超时、客户端刷新/关闭导致连接断开。
     */
    @ExceptionHandler({
            AsyncRequestTimeoutException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleAsyncRequestLifecycleException(Exception ex, HttpServletRequest request) {
        log.debug("异步请求已结束或客户端已断开: uri={}, error={}",
                request.getRequestURI(), ex.getClass().getSimpleName());
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleGenericException(Exception ex, HttpServletRequest request) {
        // 响应已提交或 SSE 等流式响应，无法再写入错误
        if (ex instanceof HttpMessageNotWritableException) {
            log.debug("响应已提交，写入失败: {}", ex.getMessage());
            return null;
        }
        log.error("Unexpected error occurred", ex);
        // 不返回具体错误信息，避免泄露敏感数据
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}
