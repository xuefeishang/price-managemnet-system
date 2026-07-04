package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.Department;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.service.DepartmentService;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final OperationLogHelper operationLogHelper;

    /**
     * 获取部门树
     */
    @GetMapping("/tree")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Department>> getDepartmentTree() {
        List<Department> tree = departmentService.getDepartmentTree();
        return Result.success("获取部门树成功", tree);
    }

    /**
     * 获取所有部门列表（扁平）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Department>> getAllDepartments() {
        List<Department> departments = departmentService.getActiveDepartments();
        return Result.success("获取部门列表成功", departments);
    }

    /**
     * 获取部门详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Department> getDepartment(@PathVariable Long id) {
        return departmentService.getDepartmentById(id)
                .map(dept -> Result.success("获取部门成功", dept))
                .orElse(Result.error(404, "部门不存在"));
    }

    /**
     * 创建部门
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Department> createDepartment(@RequestBody Department department) {
        try {
            Department saved = departmentService.createDepartment(department);
            operationLogHelper.logSuccess("部门管理", OperationLog.OperationType.CREATE,
                    "创建部门：" + saved.getDeptName(), "部门编码：" + saved.getDeptCode());
            return Result.success("创建部门成功", saved);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("部门管理", OperationLog.OperationType.CREATE,
                    "创建部门失败", department.getDeptCode(), e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新部门
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Department> updateDepartment(@PathVariable Long id, @RequestBody Department department) {
        try {
            Department saved = departmentService.updateDepartment(id, department);
            operationLogHelper.logSuccess("部门管理", OperationLog.OperationType.UPDATE,
                    "更新部门：" + saved.getDeptName(), "部门ID：" + id);
            return Result.success("更新部门成功", saved);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("部门管理", OperationLog.OperationType.UPDATE,
                    "更新部门失败", "部门ID：" + id, e.getMessage());
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 移动部门（拖拽）
     */
    @PutMapping("/{id}/move")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Department> moveDepartment(@PathVariable Long id, @RequestParam(required = false) Long parentId) {
        try {
            Department saved = departmentService.moveDepartment(id, parentId);
            operationLogHelper.logSuccess("部门管理", OperationLog.OperationType.UPDATE,
                    "移动部门：" + saved.getDeptName(), "新父部门ID：" + parentId);
            return Result.success("移动部门成功", saved);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("部门管理", OperationLog.OperationType.UPDATE,
                    "移动部门失败", "部门ID：" + id, e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 批量排序
     */
    @PutMapping("/sort")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> batchSort(@RequestBody List<Long> orderedIds) {
        try {
            departmentService.batchSort(orderedIds);
            operationLogHelper.logSuccess("部门管理", OperationLog.OperationType.UPDATE,
                    "部门排序", "排序数量：" + orderedIds.size());
            return Result.success("排序成功");
        } catch (Exception e) {
            operationLogHelper.logError("部门管理", OperationLog.OperationType.UPDATE,
                    "部门排序失败", "", e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 删除部门
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteDepartment(@PathVariable Long id) {
        try {
            String deptName = departmentService.getDepartmentById(id)
                    .map(Department::getDeptName)
                    .orElse("ID:" + id);
            departmentService.deleteDepartment(id);
            operationLogHelper.logSuccess("部门管理", OperationLog.OperationType.DELETE,
                    "删除部门：" + deptName, "部门ID：" + id);
            return Result.success("删除部门成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("部门管理", OperationLog.OperationType.DELETE,
                    "删除部门失败", "部门ID：" + id, e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }
}
