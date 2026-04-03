
package com.pricemanagement.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.pricemanagement.dto.ProductExcelData;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.Price;
import com.pricemanagement.listener.ProductExcelListener;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductCategoryRepository;
import com.pricemanagement.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            excelData.setCode(product.getCode());
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
}
