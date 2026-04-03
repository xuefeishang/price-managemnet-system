
package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<ProductCategory>> getCategories(
            @RequestParam(required = false) String status) {
        if (status != null) {
            try {
                ProductCategory.CategoryStatus categoryStatus = ProductCategory.CategoryStatus.valueOf(status);
                return Result.success("获取分类列表成功",
                        productCategoryService.getActiveCategories());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status parameter: {}", status);
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
            return Result.success("创建分类成功", savedCategory);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<ProductCategory> updateCategory(@PathVariable Long id,
                                                   @RequestBody ProductCategory category) {
        try {
            ProductCategory updatedCategory = productCategoryService.updateCategory(id, category);
            return Result.success("更新分类成功", updatedCategory);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        try {
            productCategoryService.deleteCategory(id);
            return Result.success("删除分类成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
