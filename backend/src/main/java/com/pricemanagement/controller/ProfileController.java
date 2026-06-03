package com.pricemanagement.controller;

import com.pricemanagement.dto.*;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public Result<ProfileDTO> getProfile() {
        return Result.success("获取个人资料成功", profileService.getCurrentProfile());
    }

    @PutMapping
    public Result<ProfileDTO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success("更新个人资料成功", profileService.updateCurrentProfile(request));
    }

    @GetMapping("/security")
    public Result<ProfileSecurityDTO> security() {
        return Result.success("获取账号安全信息成功", profileService.getSecurity());
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return Result.success("密码修改成功，请重新登录");
    }

    @GetMapping("/operation-logs")
    public Result<Page<OperationLog>> operationLogs(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) String operationType,
                                                    @RequestParam(required = false) String operationModule,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success("获取我的操作记录成功",
                profileService.getMyOperationLogs(page, size, operationType, operationModule, keyword, startTime, endTime));
    }

    @GetMapping("/sessions")
    public Result<List<ProfileSessionDTO>> sessions(@RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        return Result.success("获取会话列表成功", profileService.getSessions(refreshToken));
    }

    @DeleteMapping("/sessions/others")
    public Result<Void> revokeOtherSessions(@RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        profileService.revokeOtherSessions(refreshToken);
        return Result.success("其他设备已退出");
    }

    @DeleteMapping("/sessions/all")
    public Result<Void> revokeAllSessions() {
        profileService.revokeAllSessions();
        return Result.success("所有设备已退出，请重新登录");
    }

    @DeleteMapping("/sessions/{id:\\d+}")
    public Result<Void> revokeSession(@PathVariable Long id,
                                      @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        profileService.revokeSession(id, refreshToken);
        return Result.success("会话已撤销");
    }

    @GetMapping("/login-history")
    public Result<Page<ProfileLoginHistoryDTO>> loginHistory(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size,
                                                             @RequestParam(required = false) String result,
                                                             @RequestParam(required = false)
                                                             @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                                             @RequestParam(required = false)
                                                             @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success("获取登录历史成功", profileService.getLoginHistory(page, size, result, startTime, endTime));
    }

    @GetMapping("/preferences")
    public Result<ProfilePreferenceDTO> preferences() {
        return Result.success("获取个人偏好成功", profileService.getPreferences());
    }

    @PutMapping("/preferences")
    public Result<ProfilePreferenceDTO> updatePreferences(@Valid @RequestBody ProfilePreferenceUpdateRequest request) {
        return Result.success("更新个人偏好成功", profileService.updatePreferences(request));
    }
}
