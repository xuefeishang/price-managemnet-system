
package com.pricemanagement.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.dto.ProductExcelData;
import com.pricemanagement.dto.UserExcelData;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.User;
import com.pricemanagement.listener.ProductExcelListener;
import com.pricemanagement.listener.UserExcelListener;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductCategoryRepository;
import com.pricemanagement.repository.ProductRepository;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportExportService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PriceRepository priceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;

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
        UserExcelListener listener = new UserExcelListener(
            userRepository, passwordEncoder, securityProperties.getDefaultUserPassword(),
            sysRoleRepository, userRoleRepository);
        EasyExcel.read(file.getInputStream(), UserExcelData.class, listener)
                .sheet()
                .doRead();
        log.info("用户导入完成: 成功 {}, 跳过 {}", listener.getSuccessCount(), listener.getSkipCount());
        return new UserImportResult(listener.getSuccessCount(), listener.getSkipCount(), listener.getErrors());
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
        List<UserExcelData> templateData = List.of(
            createTemplateUser("zhangsan", "000001", "张三", "zhangsan@example.com", "13800138001", "技术部", "EDITOR", "ACTIVE", "初始密码123"),
            createTemplateUser("lisi", "000002", "李四", "lisi@example.com", "13800138002", "市场部", "VIEWER", "ACTIVE", null)
        );

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), UserExcelData.class)
                .sheet("用户")
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .doWrite(templateData);
    }

    private UserExcelData createTemplateUser(String username, String employeeId, String nickname,
            String email, String phone, String department, String role, String status, String password) {
        UserExcelData data = new UserExcelData();
        data.setUsername(username);
        data.setEmployeeId(employeeId);
        data.setNickname(nickname);
        data.setEmail(email);
        data.setPhone(phone);
        data.setDepartment(department);
        data.setRole(role);
        data.setStatus(status);
        data.setPassword(password);
        return data;
    }

    public record UserImportResult(int successCount, int skipCount, List<String> errors) {}
}
