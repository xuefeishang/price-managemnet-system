
package com.pricemanagement.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.pricemanagement.dto.ProductExcelData;
import com.pricemanagement.dto.UserExcelData;
import com.pricemanagement.dto.UserImportResult;
import com.pricemanagement.dto.UserImportValidationError;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.User;
import com.pricemanagement.listener.ProductExcelListener;
import com.pricemanagement.exception.UserImportValidationException;
import com.pricemanagement.listener.UserExcelValidationListener;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductCategoryRepository;
import com.pricemanagement.repository.ProductRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportExportService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PriceRepository priceRepository;
    private final UserRepository userRepository;
    private final UserImportValidationService userImportValidationService;
    private final UserImportWriteService userImportWriteService;

    public void importProducts(MultipartFile file) throws IOException {
        EasyExcel.read(file.getInputStream(), ProductExcelData.class,
                        new ProductExcelListener(productRepository, categoryRepository, priceRepository))
                .sheet()
                .doRead();
        log.info("产品导入完成，文件大小: {} bytes", file.getSize());
    }

    public void exportProducts(HttpServletResponse response) throws IOException {
        List<Product> products = productRepository.findAll();
        List<ProductExcelData> excelDataList = convertToExcelData(products);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("产品价格列表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), ProductExcelData.class)
                .sheet("产品价格")
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .doWrite(excelDataList);
        log.info("产品导出完成，共 {} 条数据", products.size());
    }

    private List<ProductExcelData> convertToExcelData(List<Product> products) {
        return products.stream().map(product -> {
            ProductExcelData excelData = new ProductExcelData();
            excelData.setName(product.getName());
            if (product.getCategory() != null) {
                excelData.setCategoryCode(product.getCategory().getCode());
            }
            excelData.setSpecs(product.getSpecs());
            excelData.setStatus(product.getStatus().toString());
            excelData.setDescription(product.getDescription());
            excelData.setRemark(product.getRemark());

            // 获取最新价格
            List<Price> prices = priceRepository.findByProductIdOrderByCreatedTimeDesc(product.getId());
            if (!prices.isEmpty()) {
                Price latestPrice = prices.get(0);
                excelData.setOriginalPrice(latestPrice.getOriginalPrice());
                excelData.setCurrentPrice(latestPrice.getCurrentPrice());
                excelData.setCostPrice(latestPrice.getCostPrice());
                excelData.setUnit(latestPrice.getUnit());
                excelData.setPriceSpec(latestPrice.getPriceSpec());
            }

            return excelData;
        }).toList();
    }

    // ==================== 用户导入导出 ====================

    public UserImportResult importUsers(MultipartFile file) throws IOException {
        validateUserImportFile(file);
        UserExcelValidationListener listener = new UserExcelValidationListener();
        try {
            EasyExcel.read(file.getInputStream(), UserExcelData.class, listener)
                    .sheet("用户")
                    .doRead();
        } catch (Exception ex) {
            UserImportResult invalid = UserImportResult.invalid(0, List.of(
                    new UserImportValidationError(null, "file", "INVALID_EXCEL_TEMPLATE", "无法读取用户导入模板")));
            throw new UserImportValidationException(invalid);
        }

        UserImportValidationService.ValidationOutcome outcome = userImportValidationService.validate(listener);
        if (!outcome.result().valid()) {
            throw new UserImportValidationException(outcome.result());
        }
        int importedCount = userImportWriteService.importValidatedRows(outcome.rows());
        log.info("用户导入完成: importedCount={}", importedCount);
        return UserImportResult.success(importedCount);
    }

    public void exportUsers(HttpServletResponse response) throws IOException {
        List<User> users = userRepository.findAll();
        List<UserExcelData> excelDataList = users.stream().map(user -> {
            UserExcelData data = new UserExcelData();
            data.setUsername(user.getUsername());
            data.setEmployeeId(user.getEmployeeId());
            data.setNickname(user.getNickname());
            data.setEmail(user.getEmail());
            data.setPhone(user.getPhone());
            data.setDepartment(user.getDepartment());
            data.setRole(user.getRole() != null ? user.getRole().name() : "VIEWER");
            data.setStatus(user.getStatus() != null ? user.getStatus().name() : "ACTIVE");
            // 密码不导出
            return data;
        }).toList();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户列表", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), UserExcelData.class)
                .sheet("用户")
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .doWrite(excelDataList);
        log.info("用户导出完成，共 {} 条数据", users.size());
    }

    public void downloadUserTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), UserExcelData.class)
                .sheet("用户")
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .doWrite(List.of());
    }

    private void validateUserImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UserImportValidationException(UserImportResult.invalid(0, List.of(
                    new UserImportValidationError(null, "file", "EMPTY_FILE", "请选择非空 Excel 文件"))));
        }
        String originalName = file.getOriginalFilename();
        String normalized = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".xlsx") && !normalized.endsWith(".xls")) {
            throw new UserImportValidationException(UserImportResult.invalid(0, List.of(
                    new UserImportValidationError(null, "file", "INVALID_FILE_TYPE", "仅支持 Excel 文件"))));
        }
    }
}
