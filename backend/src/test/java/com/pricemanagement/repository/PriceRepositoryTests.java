package com.pricemanagement.repository;

import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PriceRepositoryTests {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PriceRepository priceRepository;

    @Test
    void latestExpiredPriceDoesNotReactivateOlderPrice() {
        Product product = saveProduct();
        savePrice(product, LocalDate.of(2026, 1, 1), null, "900.00");
        savePrice(product, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "1000.00");
        LocalDate queryDate = LocalDate.of(2026, 3, 1);

        assertTrue(priceRepository.findLatestPriceBeforeDate(product.getId(), queryDate).isEmpty());
        assertTrue(priceRepository.findLatestPricesBeforeDate(List.of(product.getId()), queryDate).isEmpty());
        assertTrue(priceRepository.findPreviousEffectivePricesBeforeDate(List.of(product.getId()), queryDate).isEmpty());
    }

    @Test
    void latestPriceRemainsValidOnItsExpiryDate() {
        Product product = saveProduct();
        savePrice(product, LocalDate.of(2026, 1, 1), null, "900.00");
        Price latest = savePrice(product, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), "1000.00");
        LocalDate queryDate = LocalDate.of(2026, 2, 28);

        assertEquals(latest.getId(), priceRepository.findLatestPriceBeforeDate(product.getId(), queryDate).orElseThrow().getId());
        assertEquals(1, priceRepository.findPreviousEffectivePricesBeforeDate(List.of(product.getId()), queryDate).size());
    }

    private Product saveProduct() {
        Product product = new Product();
        product.setName("有效期测试产品");
        return productRepository.saveAndFlush(product);
    }

    private Price savePrice(Product product, LocalDate effectiveDate, LocalDate expiryDate, String currentPrice) {
        Price price = new Price();
        price.setProduct(product);
        price.setEffectiveDate(effectiveDate);
        price.setExpiryDate(expiryDate);
        price.setCurrentPrice(new BigDecimal(currentPrice));
        return priceRepository.saveAndFlush(price);
    }
}
