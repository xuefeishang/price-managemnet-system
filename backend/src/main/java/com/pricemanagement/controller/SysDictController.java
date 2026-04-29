package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.service.SysDictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService sysDictService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<SysDict>> getDicts(
            @RequestParam(required = false) String category) {
        if (category != null) {
            return Result.success("获取字典列表成功", sysDictService.getDictsByCategory(category));
        }
        return Result.success("获取字典列表成功", sysDictService.getAllDicts());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<SysDict>> getActiveDicts(
            @RequestParam String category) {
        return Result.success("获取字典列表成功", sysDictService.getActiveDictsByCategory(category));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<String>> getCategories(
            @RequestParam(required = false, defaultValue = "false") boolean all) {
        if (all) {
            return Result.success("获取字典分类成功", sysDictService.getAllCategories());
        }
        return Result.success("获取字典分类成功", sysDictService.getCategories());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<SysDict> getDict(@PathVariable Long id) {
        return sysDictService.getDictById(id)
                .map(dict -> Result.success("获取字典项成功", dict))
                .orElse(Result.error(404, "字典项不存在"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<SysDict> createDict(@RequestBody SysDict sysDict) {
        try {
            SysDict saved = sysDictService.createDict(sysDict);
            return Result.success("创建字典项成功", saved);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> batchCreateDicts(@RequestBody List<SysDict> dicts) {
        try {
            sysDictService.batchCreateDicts(dicts);
            return Result.success("批量创建字典项成功");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<SysDict> updateDict(@PathVariable Long id,
                                       @RequestBody SysDict sysDict) {
        try {
            SysDict updated = sysDictService.updateDict(id, sysDict);
            return Result.success("更新字典项成功", updated);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteDict(@PathVariable Long id) {
        try {
            sysDictService.deleteDict(id);
            return Result.success("删除字典项成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
