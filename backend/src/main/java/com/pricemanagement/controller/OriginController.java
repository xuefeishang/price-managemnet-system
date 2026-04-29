package com.pricemanagement.controller;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.Origin;
import com.pricemanagement.service.OriginService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/origins")
@RequiredArgsConstructor
public class OriginController {

    private final OriginService originService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<Origin>> getOrigins(
            @RequestParam(required = false) String status) {
        if (status != null) {
            try {
                CommonStatus originStatus = CommonStatus.valueOf(status);
                return Result.success("获取产地列表成功",
                        originService.getActiveOrigins());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status parameter: {}", status);
            }
        }
        return Result.success("获取产地列表成功", originService.getAllOrigins());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Origin> getOrigin(@PathVariable Long id) {
        return originService.getOriginById(id)
                .map(origin -> Result.success("获取产地成功", origin))
                .orElse(Result.error(404, "产地不存在"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Origin> createOrigin(@RequestBody Origin origin) {
        try {
            Origin savedOrigin = originService.createOrigin(origin);
            return Result.success("创建产地成功", savedOrigin);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Origin> updateOrigin(@PathVariable Long id,
                                       @RequestBody Origin origin) {
        try {
            Origin updatedOrigin = originService.updateOrigin(id, origin);
            return Result.success("更新产地成功", updatedOrigin);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteOrigin(@PathVariable Long id) {
        try {
            originService.deleteOrigin(id);
            return Result.success("删除产地成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
