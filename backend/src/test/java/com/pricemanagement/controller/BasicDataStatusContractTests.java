package com.pricemanagement.controller;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.controller.external.ExternalBasicDataController;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.Customer;
import com.pricemanagement.entity.Origin;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.service.CustomerService;
import com.pricemanagement.service.OriginService;
import com.pricemanagement.service.ProductCategoryService;
import com.pricemanagement.service.SysDictService;
import com.pricemanagement.util.OperationLogHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasicDataStatusContractTests {

    @Mock
    private ProductCategoryService productCategoryService;
    @Mock
    private OriginService originService;
    @Mock
    private CustomerService customerService;
    @Mock
    private SysDictService sysDictService;
    @Mock
    private OperationLogHelper operationLogHelper;

    private ProductCategoryController productCategoryController;
    private OriginController originController;
    private CustomerController customerController;
    private ExternalBasicDataController externalBasicDataController;

    @BeforeEach
    void setUp() {
        productCategoryController = new ProductCategoryController(productCategoryService, operationLogHelper);
        originController = new OriginController(originService, operationLogHelper);
        customerController = new CustomerController(customerService, operationLogHelper);
        externalBasicDataController = new ExternalBasicDataController(
                productCategoryService, originService, customerService, sysDictService);
    }

    @Test
    void internalCategoryStatusFiltersByRequestedStatus() {
        when(productCategoryService.getCategoriesByStatus(CommonStatus.INACTIVE))
                .thenReturn(Collections.emptyList());

        Result<List<ProductCategory>> result = productCategoryController.getCategories("INACTIVE");

        assertEquals(200, result.getCode());
        verify(productCategoryService).getCategoriesByStatus(CommonStatus.INACTIVE);
        verify(productCategoryService, never()).getActiveCategories();
        verify(productCategoryService, never()).getAllCategories();
    }

    @Test
    void internalOriginStatusFiltersByRequestedStatus() {
        when(originService.getOriginsByStatus(CommonStatus.INACTIVE))
                .thenReturn(Collections.emptyList());

        Result<List<Origin>> result = originController.getOrigins("INACTIVE");

        assertEquals(200, result.getCode());
        verify(originService).getOriginsByStatus(CommonStatus.INACTIVE);
        verify(originService, never()).getActiveOrigins();
        verify(originService, never()).getAllOrigins();
    }

    @Test
    void internalCustomerStatusFiltersByRequestedStatus() {
        when(customerService.getCustomersByStatus(CommonStatus.INACTIVE))
                .thenReturn(Collections.emptyList());

        Result<List<Customer>> result = customerController.getCustomers("INACTIVE");

        assertEquals(200, result.getCode());
        verify(customerService).getCustomersByStatus(CommonStatus.INACTIVE);
        verify(customerService, never()).getActiveCustomers();
        verify(customerService, never()).getAllCustomers();
    }

    @Test
    void internalInvalidStatusReturnsExplicitClientError() {
        Result<List<ProductCategory>> result = productCategoryController.getCategories("BROKEN");

        assertEquals(400, result.getCode());
        assertEquals("无效状态: BROKEN", result.getMessage());
        verifyNoInteractions(productCategoryService);
    }

    @Test
    void externalValidInactiveStatusUsesRequestedStatus() {
        when(productCategoryService.getCategoriesByStatus(CommonStatus.INACTIVE))
                .thenReturn(Collections.emptyList());

        Result<List<ProductCategory>> result = externalBasicDataController.getCategories("INACTIVE");

        assertEquals(200, result.getCode());
        verify(productCategoryService).getCategoriesByStatus(CommonStatus.INACTIVE);
        verify(productCategoryService, never()).getAllCategories();
    }

    @Test
    void externalInvalidStatusKeepsLegacyAllDataBehavior() {
        when(productCategoryService.getAllCategories()).thenReturn(Collections.emptyList());

        Result<List<ProductCategory>> result = externalBasicDataController.getCategories("BROKEN");

        assertEquals(200, result.getCode());
        verify(productCategoryService).getAllCategories();
        verify(productCategoryService, never()).getCategoriesByStatus(CommonStatus.ACTIVE);
        verify(productCategoryService, never()).getCategoriesByStatus(CommonStatus.INACTIVE);
    }

    @Test
    void externalInvalidOriginStatusKeepsLegacyAllDataBehavior() {
        when(originService.getAllOrigins()).thenReturn(Collections.emptyList());

        Result<List<Origin>> result = externalBasicDataController.getOrigins("BROKEN");

        assertEquals(200, result.getCode());
        verify(originService).getAllOrigins();
        verify(originService, never()).getOriginsByStatus(CommonStatus.ACTIVE);
        verify(originService, never()).getOriginsByStatus(CommonStatus.INACTIVE);
    }

    @Test
    void externalInvalidCustomerStatusKeepsLegacyAllDataBehavior() {
        when(customerService.getAllCustomers()).thenReturn(Collections.emptyList());

        Result<List<Customer>> result = externalBasicDataController.getCustomers("BROKEN");

        assertEquals(200, result.getCode());
        verify(customerService).getAllCustomers();
        verify(customerService, never()).getCustomersByStatus(CommonStatus.ACTIVE);
        verify(customerService, never()).getCustomersByStatus(CommonStatus.INACTIVE);
    }

    @Test
    void categoryCreateFailureRecordsBusinessResponseCode() {
        ProductCategory category = new ProductCategory();
        category.setCode("DUP");
        when(productCategoryService.createCategory(category))
                .thenThrow(new IllegalArgumentException("分类编码已存在: DUP"));

        Result<ProductCategory> result = productCategoryController.createCategory(category);

        assertEquals(400, result.getCode());
        verify(operationLogHelper).logError(eq("产品分类管理"), eq(com.pricemanagement.entity.OperationLog.OperationType.CREATE),
                eq("创建分类失败"), eq("分类编码：DUP"), eq("分类编码已存在: DUP"), eq("400"));
    }

    @Test
    void categoryDeleteFailureRecordsNotFoundResponseCode() {
        when(productCategoryService.getCategoryById(9L)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.doThrow(new IllegalArgumentException("分类不存在: 9"))
                .when(productCategoryService).deleteCategory(9L);

        Result<Void> result = productCategoryController.deleteCategory(9L);

        assertEquals(404, result.getCode());
        verify(operationLogHelper).logError(eq("产品分类管理"), eq(com.pricemanagement.entity.OperationLog.OperationType.DELETE),
                eq("删除分类失败"), eq("分类ID：9"), eq("分类不存在: 9"), eq("404"));
    }
}
