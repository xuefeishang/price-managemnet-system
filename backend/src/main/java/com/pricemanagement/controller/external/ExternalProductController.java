package com.pricemanagement.controller.external;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.ExternalProductDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.Product;
import com.pricemanagement.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/external/v1/products")
@RequiredArgsConstructor
public class ExternalProductController {

    private static final Long EXTERNAL_APPLICANT_ID = 0L;

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAuthority('API_product:read')")
    public Result<Page<ExternalProductDTO>> getProducts(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false) CommonStatus status,
                                                        @RequestParam(required = false, defaultValue = "id") String sortBy,
                                                        @RequestParam(required = false, defaultValue = "asc") String sortDirection) {
        Page<ExternalProductDTO> products = productService
                .getProducts(page, size, keyword, categoryId, status, sortBy, sortDirection)
                .map(ExternalProductDTO::from);
        return Result.success("获取产品列表成功", products);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('API_product:read')")
    public Result<ExternalProductDTO> getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ExternalProductDTO::from)
                .map(product -> Result.success("获取产品成功", product))
                .orElse(Result.error(404, "产品不存在"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('API_product:write')")
    public Result<ExternalProductDTO> createProduct(@Valid @RequestBody Product product) {
        product.setId(null);
        product.setVersion(null);
        product.setCreatedTime(null);
        product.setUpdatedTime(null);
        Product saved = productService.createProduct(product, EXTERNAL_APPLICANT_ID);
        return Result.success("创建产品成功", ExternalProductDTO.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('API_product:write')")
    public Result<ExternalProductDTO> updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        Product saved = productService.updateProduct(id, product);
        return Result.success("更新产品成功", ExternalProductDTO.from(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('API_product:delete')")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success("删除产品成功");
    }
}
