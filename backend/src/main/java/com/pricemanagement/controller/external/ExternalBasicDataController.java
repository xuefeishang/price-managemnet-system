package com.pricemanagement.controller.external;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.Result;
import com.pricemanagement.dto.ExternalApiPrincipal;
import com.pricemanagement.entity.Customer;
import com.pricemanagement.entity.Origin;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.service.CustomerService;
import com.pricemanagement.service.OriginService;
import com.pricemanagement.service.ProductCategoryService;
import com.pricemanagement.service.SysDictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/external/v1")
@RequiredArgsConstructor
public class ExternalBasicDataController {

    private final ProductCategoryService productCategoryService;
    private final OriginService originService;
    private final CustomerService customerService;
    private final SysDictService sysDictService;

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('API_category:read')")
    public Result<List<ProductCategory>> getCategories(@RequestParam(required = false) String status) {
        CommonStatus categoryStatus = parseExternalStatus("categories", status);
        if (categoryStatus != null) {
            return Result.success("获取分类列表成功", productCategoryService.getCategoriesByStatus(categoryStatus));
        }
        return Result.success("获取分类列表成功", productCategoryService.getAllCategories());
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('API_category:read')")
    public Result<ProductCategory> getCategory(@PathVariable Long id) {
        return productCategoryService.getCategoryById(id)
                .map(category -> Result.success("获取分类成功", category))
                .orElse(Result.error(404, "分类不存在"));
    }

    @GetMapping("/origins")
    @PreAuthorize("hasAuthority('API_origin:read')")
    public Result<List<Origin>> getOrigins(@RequestParam(required = false) String status) {
        CommonStatus originStatus = parseExternalStatus("origins", status);
        if (originStatus != null) {
            return Result.success("获取产地列表成功", originService.getOriginsByStatus(originStatus));
        }
        return Result.success("获取产地列表成功", originService.getAllOrigins());
    }

    @GetMapping("/origins/{id}")
    @PreAuthorize("hasAuthority('API_origin:read')")
    public Result<Origin> getOrigin(@PathVariable Long id) {
        return originService.getOriginById(id)
                .map(origin -> Result.success("获取产地成功", origin))
                .orElse(Result.error(404, "产地不存在"));
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('API_customer:read')")
    public Result<List<Customer>> getCustomers(@RequestParam(required = false) String status) {
        CommonStatus customerStatus = parseExternalStatus("customers", status);
        if (customerStatus != null) {
            return Result.success("获取客户列表成功", customerService.getCustomersByStatus(customerStatus));
        }
        return Result.success("获取客户列表成功", customerService.getAllCustomers());
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('API_customer:read')")
    public Result<Customer> getCustomer(@PathVariable Long id) {
        return customerService.getCustomerById(id)
                .map(customer -> Result.success("获取客户成功", customer))
                .orElse(Result.error(404, "客户不存在"));
    }

    @GetMapping("/dict")
    @PreAuthorize("hasAuthority('API_dict:read')")
    public Result<List<SysDict>> getDicts(@RequestParam(required = false) String category) {
        if (category == null || category.isBlank()) {
            return Result.error(400, "外部字典接口必须指定category");
        }
        return Result.success("获取字典列表成功", sysDictService.getDictsByCategory(category));
    }

    @GetMapping("/dict/active")
    @PreAuthorize("hasAuthority('API_dict:read')")
    public Result<List<SysDict>> getActiveDicts(@RequestParam String category) {
        return Result.success("获取字典列表成功", sysDictService.getActiveDictsByCategory(category));
    }

    @GetMapping("/dict/categories")
    @PreAuthorize("hasAuthority('API_dict:read')")
    public Result<List<String>> getDictCategories(@RequestParam(required = false, defaultValue = "false") boolean all) {
        return Result.success("获取字典分类成功", all ? sysDictService.getAllCategories() : sysDictService.getCategories());
    }

    @GetMapping("/dict/{id}")
    @PreAuthorize("hasAuthority('API_dict:read')")
    public Result<SysDict> getDict(@PathVariable Long id) {
        return sysDictService.getDictById(id)
                .map(dict -> Result.success("获取字典项成功", dict))
                .orElse(Result.error(404, "字典项不存在"));
    }

    private CommonStatus parseExternalStatus(String resource, String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CommonStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            log.warn("External basic data invalid status ignored: resource={}, status={}, appId={}, uri={}",
                    resource, status, currentAppId(), currentRequestUri());
            return null;
        }
    }

    private String currentAppId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return "unknown";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof ExternalApiPrincipal externalApiPrincipal) {
            return externalApiPrincipal.appId();
        }
        return authentication.getName();
    }

    private String currentRequestUri() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRequestURI();
        }
        return "unknown";
    }
}
