
package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.Product;
import com.pricemanagement.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;

    @BeforeEach
    void setup() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("测试产品");
        testProduct.setStatus(CommonStatus.ACTIVE);
    }

    @Test
    void testGetProductById() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        Optional<Product> result = productService.getProductById(1L);

        assertTrue(result.isPresent());
        assertEquals("测试产品", result.get().getName());
    }

    @Test
    void testGetProductByIdNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<Product> result = productService.getProductById(1L);

        assertFalse(result.isPresent());
    }

    @Test
    void testGetProductsWithKeyword() {
        List<Product> products = new ArrayList<>();
        products.add(testProduct);
        Page<Product> page = new PageImpl<>(products);
        Pageable pageable = PageRequest.of(0, 10);

        when(productRepository.findByNameContaining(anyString(), any(Pageable.class)))
                .thenReturn(page);

        Page<Product> result = productService.getProducts(0, 10, "测试", null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("测试产品", result.getContent().get(0).getName());
    }

    @Test
    void testCreateProductSuccess() {
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        Product created = productService.createProduct(testProduct, null);

        assertNotNull(created);
        assertEquals("测试产品", created.getName());
    }

    @Test
    void testUpdateProductSuccess() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        Product updatedProduct = new Product();
        updatedProduct.setName("更新后的产品");

        Product result = productService.updateProduct(1L, updatedProduct);

        assertNotNull(result);
        assertEquals("更新后的产品", result.getName());
    }

    @Test
    void testUpdateProductNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        Product updatedProduct = new Product();

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.updateProduct(1L, updatedProduct);
        });

        assertTrue(exception.getMessage().contains("产品不存在"));
    }

    @Test
    void testDeleteProductSuccess() {
        when(productRepository.existsById(1L)).thenReturn(true);
        doNothing().when(productRepository).deleteById(1L);

        productService.deleteProduct(1L);

        verify(productRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteProductNotFound() {
        when(productRepository.existsById(1L)).thenReturn(false);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            productService.deleteProduct(1L);
        });

        assertTrue(exception.getMessage().contains("产品不存在"));
    }
}
