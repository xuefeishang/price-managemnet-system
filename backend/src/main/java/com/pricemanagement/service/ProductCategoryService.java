
package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;

    public List<ProductCategory> getAllCategories() {
        return productCategoryRepository.findAll();
    }

    public List<ProductCategory> getActiveCategories() {
        return productCategoryRepository.findByStatusOrderBySortOrderAsc(CommonStatus.ACTIVE);
    }

    public Optional<ProductCategory> getCategoryById(Long id) {
        return productCategoryRepository.findById(id);
    }

    public Optional<ProductCategory> getCategoryByCode(String code) {
        return productCategoryRepository.findByCode(code);
    }

    @Transactional
    public ProductCategory createCategory(ProductCategory category) {
        if (productCategoryRepository.existsByCode(category.getCode())) {
            throw new IllegalArgumentException("分类编码已存在: " + category.getCode());
        }
        ProductCategory savedCategory = productCategoryRepository.save(category);
        log.info("Created category: {}", savedCategory.getName());
        return savedCategory;
    }

    @Transactional
    public ProductCategory updateCategory(Long id, ProductCategory category) {
        ProductCategory existingCategory = productCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分类不存在: " + id));

        if (category.getName() != null) {
            existingCategory.setName(category.getName());
        }
        if (category.getCode() != null && !category.getCode().equals(existingCategory.getCode())) {
            if (productCategoryRepository.existsByCode(category.getCode())) {
                throw new IllegalArgumentException("分类编码已存在: " + category.getCode());
            }
            existingCategory.setCode(category.getCode());
        }
        if (category.getStatus() != null) {
            existingCategory.setStatus(category.getStatus());
        }
        if (category.getSortOrder() != null) {
            existingCategory.setSortOrder(category.getSortOrder());
        }
        if (category.getRemark() != null) {
            existingCategory.setRemark(category.getRemark());
        }

        ProductCategory savedCategory = productCategoryRepository.save(existingCategory);
        log.info("Updated category: {}", savedCategory.getName());
        return savedCategory;
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!productCategoryRepository.existsById(id)) {
            throw new IllegalArgumentException("分类不存在: " + id);
        }
        productCategoryRepository.deleteById(id);
        log.info("Deleted category with id: {}", id);
    }

    @Transactional
    public void batchUpdateSort(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<Long> ids = items.stream()
                .map(item -> ((Number) item.get("id")).longValue())
                .collect(Collectors.toList());
        List<ProductCategory> categories = productCategoryRepository.findAllById(ids);
        Map<Long, ProductCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, category -> category));

        if (categoryMap.size() != ids.size()) {
            throw new IllegalArgumentException("部分分类不存在");
        }

        List<ProductCategory> toSave = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            Long id = ((Number) item.get("id")).longValue();
            Integer sortOrder = ((Number) item.get("sortOrder")).intValue();
            ProductCategory category = categoryMap.get(id);
            category.setSortOrder(sortOrder);
            toSave.add(category);
        }

        productCategoryRepository.saveAll(toSave);
        log.info("Batch updated sort order for {} categories", toSave.size());
    }

    public boolean existsByCode(String code) {
        return productCategoryRepository.existsByCode(code);
    }
}
