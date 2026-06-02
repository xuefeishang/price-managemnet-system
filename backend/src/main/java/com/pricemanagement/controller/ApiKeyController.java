package com.pricemanagement.controller;

import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.dto.ApiKeyCreateRequest;
import com.pricemanagement.dto.ApiKeyCreateResponse;
import com.pricemanagement.dto.ApiKeyDTO;
import com.pricemanagement.dto.ApiKeyUpdateRequest;
import com.pricemanagement.dto.ExternalApiServiceStatusDTO;
import com.pricemanagement.dto.ExternalApiServiceStatusUpdateRequest;
import com.pricemanagement.dto.ExternalApiEndpointDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog.OperationType;
import com.pricemanagement.service.ApiKeyService;
import com.pricemanagement.service.ExternalApiServiceStatusService;
import com.pricemanagement.util.IpAddressUtil;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ExternalApiServiceStatusService serviceStatusService;

    @GetMapping
    public Result<Page<ApiKeyDTO>> query(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String environment) {
        return Result.success("获取API Key列表成功", apiKeyService.query(page, size, keyword, status, environment));
    }

    @PostMapping
    @OperationLog(module = "API授权管理", type = OperationType.CREATE, description = "创建API Key", logResponse = false)
    public Result<ApiKeyCreateResponse> create(@Valid @RequestBody ApiKeyCreateRequest request,
                                               HttpServletRequest httpRequest) {
        return Result.success("创建API Key成功", apiKeyService.create(request, IpAddressUtil.getClientIp(httpRequest)));
    }

    @GetMapping("/{id}")
    public Result<ApiKeyDTO> get(@PathVariable Long id) {
        return Result.success("获取API Key详情成功", apiKeyService.get(id));
    }

    @PutMapping("/{id}")
    @OperationLog(module = "API授权管理", type = OperationType.UPDATE, description = "更新API Key")
    public Result<ApiKeyDTO> update(@PathVariable Long id,
                                    @Valid @RequestBody ApiKeyUpdateRequest request,
                                    HttpServletRequest httpRequest) {
        return Result.success("更新API Key成功", apiKeyService.update(id, request, IpAddressUtil.getClientIp(httpRequest)));
    }

    @PutMapping("/{id}/enable")
    @OperationLog(module = "API授权管理", type = OperationType.UPDATE, description = "启用API Key")
    public Result<ApiKeyDTO> enable(@PathVariable Long id, HttpServletRequest request) {
        return Result.success("启用API Key成功", apiKeyService.enable(id, IpAddressUtil.getClientIp(request)));
    }

    @PutMapping("/{id}/disable")
    @OperationLog(module = "API授权管理", type = OperationType.UPDATE, description = "停用API Key")
    public Result<ApiKeyDTO> disable(@PathVariable Long id, HttpServletRequest request) {
        return Result.success("停用API Key成功", apiKeyService.disable(id, IpAddressUtil.getClientIp(request)));
    }

    @PutMapping("/{id}/revoke")
    @OperationLog(module = "API授权管理", type = OperationType.DELETE, description = "吊销API Key")
    public Result<ApiKeyDTO> revoke(@PathVariable Long id, HttpServletRequest request) {
        return Result.success("吊销API Key成功", apiKeyService.revoke(id, IpAddressUtil.getClientIp(request)));
    }

    @GetMapping("/permissions/tree")
    public Result<List<ExternalApiEndpointDTO>> permissions() {
        return Result.success("获取外部API权限成功", apiKeyService.getPermissionEndpoints());
    }

    @GetMapping("/service-status")
    public Result<ExternalApiServiceStatusDTO> serviceStatus() {
        return Result.success("获取外部API服务状态成功", serviceStatusService.getStatus());
    }

    @PutMapping("/service-status")
    @OperationLog(module = "API授权管理", type = OperationType.UPDATE, description = "更新外部API服务状态")
    public Result<ExternalApiServiceStatusDTO> updateServiceStatus(
            @Valid @RequestBody ExternalApiServiceStatusUpdateRequest request) {
        return Result.success("更新外部API服务状态成功",
                serviceStatusService.updateRuntimeEnabled(request.getEnabled()));
    }
}
