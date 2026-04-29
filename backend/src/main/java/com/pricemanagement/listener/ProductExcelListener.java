
package com.pricemanagement.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.util.ListUtils;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.ProductExcelData;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.repository.ProductRepository;
import com.pricemanagement.repository.ProductCategoryRepository;
import com.pricemanagement.repository.PriceRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
public class ProductExcelListener implements ReadListener<ProductExcelData> {

    private static final int BATCH_COUNT = 20;
    private List<ProductExcelData> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final PriceRepository priceRepository;

    public ProductExcelListener(ProductRepository productRepository,
                                ProductCategoryRepository categoryRepository,
                                PriceRepository priceRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.priceRepository = priceRepository;
    }

    @Override
    public void invoke(ProductExcelData data, AnalysisContext context) {
        log.debug("解析到一条数据: {}", data);
        cachedDataList.add(data);
        if (cachedDataList.size() >= BATCH_COUNT) {
            saveData();
            cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        saveData();
        log.debug("所有数据解析完成！");
    }

    private void saveData() {
        log.debug("{}条数据，开始存储数据库！", cachedDataList.size());
        for (ProductExcelData excelData : cachedDataList) {
            try {
                // 查找或创建分类
                ProductCategory category = findOrCreateCategory(excelData.getCategoryCode());

                // 创建产品
                Product product = new Product();
                product.setName(excelData.getName());
                product.setCategory(category);
                product.setSpecs(excelData.getSpecs());
                product.setStatus(excelData.getStatus() != null ?
                        CommonStatus.valueOf(excelData.getStatus()) :
                        CommonStatus.ACTIVE);
                product.setDescription(excelData.getDescription());
                product.setRemark(excelData.getRemark());

                productRepository.save(product);

                // 创建价格信息
                if (excelData.getCurrentPrice() != null) {
                    Price price = new Price();
                    price.setProduct(product);
                    price.setOriginalPrice(excelData.getOriginalPrice());
                    price.setCurrentPrice(excelData.getCurrentPrice());
                    price.setCostPrice(excelData.getCostPrice());
                    price.setUnit(excelData.getUnit());
                    price.setPriceSpec(excelData.getPriceSpec());
                    priceRepository.save(price);
                }

                log.debug("保存产品成功: {}", product.getName());
            } catch (Exception e) {
                log.error("保存产品失败: {}", e.getMessage());
            }
        }
    }

    private ProductCategory findOrCreateCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isEmpty()) {
            return null;
        }
        Optional<ProductCategory> categoryOptional = categoryRepository.findByCode(categoryCode);
        if (categoryOptional.isPresent()) {
            return categoryOptional.get();
        }

        ProductCategory category = new ProductCategory();
        category.setCode(categoryCode);
        category.setName(categoryCode); // 用编码作为名称
        category.setStatus(CommonStatus.ACTIVE);
        return categoryRepository.save(category);
    }
}
