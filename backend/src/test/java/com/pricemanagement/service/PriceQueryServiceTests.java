package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.PriceQueryRowDTO;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.Product;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductRepository;
import com.pricemanagement.repository.SysDictRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceQueryServiceTests {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private PriceRepository priceRepository;
    @Mock
    private SysDictRepository sysDictRepository;
    @Mock
    private ProductAnnualBudgetService annualBudgetService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PriceQueryService priceQueryService;

    @Test
    void queryUsesLatestEffectivePriceDateForMetricRangesAndBudgetYear() {
        Product product = product(32L);
        LocalDate queryDate = LocalDate.of(2026, 1, 5);
        LocalDate metricBaseDate = LocalDate.of(2025, 12, 31);
        Price latest = price(product, metricBaseDate, "1050.00");
        Price previous = price(product, LocalDate.of(2025, 12, 20), "1000.00");
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(priceRepository.findLatestPricesBeforeDate(List.of(32L), queryDate)).thenReturn(List.of(latest));
        when(priceRepository.findPreviousEffectivePricesBeforeDate(List.of(32L), queryDate)).thenReturn(List.of(previous));
        when(priceRepository.findAveragePricesByProductIdsAndMonth(
                List.of(32L), LocalDate.of(2025, 12, 1), metricBaseDate))
                .thenReturn(List.<Object[]>of(new Object[]{32L, new BigDecimal("1025.00")}));
        when(priceRepository.findAveragePricesByProductIdsAndMonth(
                List.of(32L), LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30)))
                .thenReturn(List.<Object[]>of(new Object[]{32L, new BigDecimal("950.00")}));
        when(priceRepository.findAveragePricesByProductIdsAndMonth(
                List.of(32L), LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 31)))
                .thenReturn(List.<Object[]>of(new Object[]{32L, new BigDecimal("900.00")}));
        when(annualBudgetService.getBudgetPriceMap(List.of(32L), LocalDate.of(2025, 1, 1)))
                .thenReturn(Map.of(32L, new BigDecimal("980.00")));

        Page<PriceQueryRowDTO> result = priceQueryService.query(
                queryDate, null, null, CommonStatus.ACTIVE, 0, 10, null, null);

        PriceQueryRowDTO row = result.getContent().getFirst();
        assertEquals(new BigDecimal("980.00"), row.getBudgetPrice());
        assertEquals(row.getBudgetPrice(), row.getYesterdayPrice());
        assertEquals(row.getBudgetChangeAmount(), row.getChangeAmount());
        assertEquals(row.getBudgetChangePercent(), row.getChangePercent());
        assertEquals(new BigDecimal("1025.00"), row.getMonthlyAveragePrice());
        assertEquals(new BigDecimal("950.00"), row.getPreviousMonthAveragePrice());
        assertEquals(new BigDecimal("900.00"), row.getLastYearSamePeriodAveragePrice());
        verify(annualBudgetService).getBudgetPriceMap(List.of(32L), LocalDate.of(2025, 1, 1));
        verify(annualBudgetService, never()).getBudgetPriceMap(List.of(32L), queryDate);
    }

    @Test
    void queryUsesFebruaryTwentyEightForLeapDayLastYearSamePeriod() {
        Product product = product(32L);
        LocalDate queryDate = LocalDate.of(2024, 3, 1);
        Price latest = price(product, LocalDate.of(2024, 2, 29), "1050.00");
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(priceRepository.findLatestPricesBeforeDate(List.of(32L), queryDate)).thenReturn(List.of(latest));
        when(priceRepository.findPreviousEffectivePricesBeforeDate(List.of(32L), queryDate)).thenReturn(List.of());
        when(annualBudgetService.getBudgetPriceMap(List.of(32L), LocalDate.of(2024, 1, 1))).thenReturn(Map.of());

        priceQueryService.query(queryDate, null, null, CommonStatus.ACTIVE, 0, 10, null, null);

        verify(priceRepository).findAveragePricesByProductIdsAndMonth(
                List.of(32L), LocalDate.of(2023, 2, 1), LocalDate.of(2023, 2, 28));
    }

    @Test
    void queryLeavesMetricsEmptyWhenNoLatestValidPriceExists() {
        Product product = product(32L);
        LocalDate queryDate = LocalDate.of(2026, 1, 5);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(priceRepository.findLatestPricesBeforeDate(List.of(32L), queryDate)).thenReturn(List.of());
        when(priceRepository.findPreviousEffectivePricesBeforeDate(List.of(32L), queryDate)).thenReturn(List.of());

        PriceQueryRowDTO row = priceQueryService.query(
                queryDate, null, null, CommonStatus.ACTIVE, 0, 10, null, null).getContent().getFirst();

        assertEquals(false, row.getHasPrice());
        assertNull(row.getLatestPrice());
        assertNull(row.getBudgetPrice());
        assertNull(row.getMonthlyAveragePrice());
        verify(annualBudgetService, never()).getBudgetPriceMap(any(), any());
        verify(priceRepository, never()).findAveragePricesByProductIdsAndMonth(any(), any(), any());
    }

    private Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("测试产品");
        product.setCurrency("CNY");
        return product;
    }

    private Price price(Product product, LocalDate effectiveDate, String currentPrice) {
        Price price = new Price();
        price.setProduct(product);
        price.setEffectiveDate(effectiveDate);
        price.setCurrentPrice(new BigDecimal(currentPrice));
        return price;
    }
}
