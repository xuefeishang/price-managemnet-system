package com.pricemanagement.controller;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.Customer;
import com.pricemanagement.service.CustomerService;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final OperationLogHelper operationLogHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<Customer>> getCustomers(
            @RequestParam(required = false) String status) {
        if (status != null) {
            try {
                CommonStatus customerStatus = CommonStatus.valueOf(status);
                return Result.success("获取客户列表成功",
                        customerService.getCustomersByStatus(customerStatus));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status parameter: {}", status);
                return Result.error(400, "无效状态: " + status);
            }
        }
        return Result.success("获取客户列表成功", customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Customer> getCustomer(@PathVariable Long id) {
        return customerService.getCustomerById(id)
                .map(customer -> Result.success("获取客户成功", customer))
                .orElse(Result.error(404, "客户不存在"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Customer> createCustomer(@RequestBody Customer customer) {
        try {
            Customer savedCustomer = customerService.createCustomer(customer);
            operationLogHelper.logSuccess("客户管理", OperationLog.OperationType.CREATE,
                    "创建客户：" + savedCustomer.getName(), "客户编码：" + savedCustomer.getCode());
            return Result.success("创建客户成功", savedCustomer);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("客户管理", OperationLog.OperationType.CREATE,
                    "创建客户失败", customer == null ? "" : "客户编码：" + customer.getCode(), e.getMessage(), "400");
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Customer> updateCustomer(@PathVariable Long id,
                                          @RequestBody Customer customer) {
        try {
            Customer updatedCustomer = customerService.updateCustomer(id, customer);
            operationLogHelper.logSuccess("客户管理", OperationLog.OperationType.UPDATE,
                    "更新客户：" + updatedCustomer.getName(), "客户ID：" + id);
            return Result.success("更新客户成功", updatedCustomer);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("客户管理", OperationLog.OperationType.UPDATE,
                    "更新客户失败", "客户ID：" + id, e.getMessage(), "404");
            return Result.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteCustomer(@PathVariable Long id) {
        try {
            String customerName = customerService.getCustomerById(id)
                    .map(Customer::getName)
                    .orElse("ID:" + id);
            customerService.deleteCustomer(id);
            operationLogHelper.logSuccess("客户管理", OperationLog.OperationType.DELETE,
                    "删除客户：" + customerName, "客户ID：" + id);
            return Result.success("删除客户成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("客户管理", OperationLog.OperationType.DELETE,
                    "删除客户失败", "客户ID：" + id, e.getMessage(), "404");
            return Result.error(404, e.getMessage());
        }
    }
}
