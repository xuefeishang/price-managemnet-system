
package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.repository.ProductCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTests {

    @Mock
    private ProductCategoryRepository categoryRepository;

    @InjectMocks
    private ProductCategoryService categoryService;

    private ProductCategory testCategory;

    @BeforeEach
    void setup() {
        testCategory = new ProductCategory();
        testCategory.setId(1L);
        testCategory.setCode("C001");
        testCategory.setName("测试分类");
        testCategory.setStatus(CommonStatus.ACTIVE);
    }

    @Test
    void testGetAllCategories() {
        List<ProductCategory> categories = new ArrayList<>();
        categories.add(testCategory);

        when(categoryRepository.findAll()).thenReturn(categories);

        List<ProductCategory> result = categoryService.getAllCategories();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("C001", result.get(0).getCode());
    }

    @Test
    void testGetActiveCategories() {
        List<ProductCategory> categories = new ArrayList<>();
        categories.add(testCategory);

        when(categoryRepository.findByStatusOrderBySortOrderAsc(any()))
                .thenReturn(categories);

        List<ProductCategory> result = categoryService.getActiveCategories();

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testGetCategoryById() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));

        Optional<ProductCategory> result = categoryService.getCategoryById(1L);

        assertTrue(result.isPresent());
        assertEquals("测试分类", result.get().getName());
    }

    @Test
    void testCreateCategorySuccess() {
        when(categoryRepository.existsByCode(anyString())).thenReturn(false);
        when(categoryRepository.save(any(ProductCategory.class))).thenReturn(testCategory);

        ProductCategory created = categoryService.createCategory(testCategory);

        assertNotNull(created);
        assertEquals("C001", created.getCode());
    }

    @Test
    void testCreateCategoryDuplicateCode() {
        when(categoryRepository.existsByCode(anyString())).thenReturn(true);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            categoryService.createCategory(testCategory);
        });

        assertTrue(exception.getMessage().contains("分类编码已存在"));
    }
}
