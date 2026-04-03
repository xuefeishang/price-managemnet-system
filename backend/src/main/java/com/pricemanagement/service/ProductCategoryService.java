
package com.pricemanagement.service;

import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;

    public List<ProductCategory> getAllCategories() {
        return productCategoryRepository.findAll();
    }

    public List<ProductCategory> getActiveCategories() {
        return productCategoryRepository.findByStatusOrderBySortOrderAsc(ProductCategory.CategoryStatus.ACTIVE);
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

    public boolean existsByCode(String code) {
        return productCategoryRepository.existsByCode(code);
    }
}

