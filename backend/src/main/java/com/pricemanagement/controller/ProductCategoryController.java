
package com.pricemanagement.controller;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.service.ProductCategoryService;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;
    private final OperationLogHelper operationLogHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<ProductCategory>> getCategories(
            @RequestParam(required = false) String status) {
        if (status != null) {
            try {
                CommonStatus categoryStatus = CommonStatus.valueOf(status);
                return Result.success("获取分类列表成功",
                        productCategoryService.getCategoriesByStatus(categoryStatus));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status parameter: {}", status);
                return Result.error(400, "无效状态: " + status);
            }
        }
        return Result.success("获取分类列表成功", productCategoryService.getAllCategories());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<ProductCategory> getCategory(@PathVariable Long id) {
        return productCategoryService.getCategoryById(id)
                .map(category -> Result.success("获取分类成功", category))
                .orElse(Result.error(404, "分类不存在"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<ProductCategory> createCategory(@RequestBody ProductCategory category) {
        try {
            ProductCategory savedCategory = productCategoryService.createCategory(category);
            operationLogHelper.logSuccess("产品分类管理", OperationLog.OperationType.CREATE,
                    "创建分类：" + savedCategory.getName(), "分类编码：" + savedCategory.getCode());
            return Result.success("创建分类成功", savedCategory);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("产品分类管理", OperationLog.OperationType.CREATE,
                    "创建分类失败", category == null ? "" : "分类编码：" + category.getCode(), e.getMessage(), "400");
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<ProductCategory> updateCategory(@PathVariable Long id,
                                                   @RequestBody ProductCategory category) {
        try {
            ProductCategory updatedCategory = productCategoryService.updateCategory(id, category);
            operationLogHelper.logSuccess("产品分类管理", OperationLog.OperationType.UPDATE,
                    "更新分类：" + updatedCategory.getName(), "分类ID：" + id);
            return Result.success("更新分类成功", updatedCategory);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("产品分类管理", OperationLog.OperationType.UPDATE,
                    "更新分类失败", "分类ID：" + id, e.getMessage(), "404");
            return Result.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        try {
            String categoryName = productCategoryService.getCategoryById(id)
                    .map(ProductCategory::getName)
                    .orElse("ID:" + id);
            productCategoryService.deleteCategory(id);
            operationLogHelper.logSuccess("产品分类管理", OperationLog.OperationType.DELETE,
                    "删除分类：" + categoryName, "分类ID：" + id);
            return Result.success("删除分类成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("产品分类管理", OperationLog.OperationType.DELETE,
                    "删除分类失败", "分类ID：" + id, e.getMessage(), "404");
            return Result.error(404, e.getMessage());
        }
    }

    @PostMapping("/batch-sort")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Void> batchUpdateSort(@RequestBody List<Map<String, Object>> items) {
        try {
            productCategoryService.batchUpdateSort(items);
            operationLogHelper.logSuccess("产品分类管理", OperationLog.OperationType.UPDATE,
                    "批量更新分类排序", "数量：" + (items == null ? 0 : items.size()));
            return Result.success("批量更新分类排序成功");
        } catch (Exception e) {
            operationLogHelper.logError("产品分类管理", OperationLog.OperationType.UPDATE,
                    "批量更新分类排序失败", "", e.getMessage());
            return Result.error(500, "批量更新分类排序失败: " + e.getMessage());
        }
    }
}
