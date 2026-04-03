
package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.Product;
import com.pricemanagement.service.ProductService;
import com.pricemanagement.util.OperationLogHelper;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final OperationLogHelper operationLogHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Page<Product>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Product.ProductStatus status,
            @RequestParam(required = false, defaultValue = "id") String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection) {
        Page<Product> products = productService.getProducts(page, size, keyword, categoryId, status, sortBy, sortDirection);
        return Result.success("获取产品列表成功", products);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Product> getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> Result.success("获取产品成功", product))
                .orElse(Result.error(404, "产品不存在"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Product> createProduct(@RequestBody Product product) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Product savedProduct = productService.createProduct(product, userId);
            if ("PENDING_APPROVAL".equals(savedProduct.getRemark())) {
                operationLogHelper.logSuccess("产品管理", OperationLog.OperationType.CREATE,
                        "创建产品待审批", "产品编码：" + product.getCode());
                return Result.success("产品已提交，等待审批", savedProduct);
            }
            operationLogHelper.logSuccess("产品管理", OperationLog.OperationType.CREATE,
                    "创建产品：" + savedProduct.getName(), "产品编码：" + savedProduct.getCode());
            return Result.success("创建产品成功", savedProduct);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("产品管理", OperationLog.OperationType.CREATE,
                    "创建产品失败", product.getCode(), e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Product> updateProduct(@PathVariable Long id,
                                         @RequestBody Product product) {
        try {
            Product updatedProduct = productService.updateProduct(id, product);
            operationLogHelper.logSuccess("产品管理", OperationLog.OperationType.UPDATE,
                    "更新产品：" + updatedProduct.getName(), "产品ID：" + id);
            return Result.success("更新产品成功", updatedProduct);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("产品管理", OperationLog.OperationType.UPDATE,
                    "更新产品失败", "产品ID：" + id, e.getMessage());
            return Result.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        try {
            // 获取产品信息用于日志
            Optional<Product> productOpt = productService.getProductById(id);
            String productName = productOpt.map(Product::getName).orElse("ID:" + id);

            productService.deleteProduct(id);
            operationLogHelper.logSuccess("产品管理", OperationLog.OperationType.DELETE,
                    "删除产品：" + productName, "产品ID：" + id);
            return Result.success("删除产品成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("产品管理", OperationLog.OperationType.DELETE,
                    "删除产品失败", "产品ID：" + id, e.getMessage());
            return Result.error(404, e.getMessage());
        }
    }
}
