package com.pricemanagement.service;

import com.pricemanagement.repository.ApprovalRequestRepository;
import com.pricemanagement.repository.ApprovalWorkflowRepository;
import com.pricemanagement.repository.PriceHistoryRepository;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductRepository;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.Product;
import com.pricemanagement.dto.PriceTrendDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceServiceTests {

    @Mock
    private PriceRepository priceRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PriceHistoryRepository priceHistoryRepository;
    @Mock
    private ApprovalWorkflowRepository workflowRepository;
    @Mock
    private ApprovalRequestRepository requestRepository;
    @Mock
    private ProductAnnualBudgetService annualBudgetService;

    @InjectMocks
    private PriceService priceService;

    @Test
    void getPriceTrendUsesExactNaturalYearRangeForLeapYear() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        when(priceRepository.findByProductIdAndDateRange(32L, startDate, endDate)).thenReturn(List.of());
        when(annualBudgetService.getBudgetPriceMapByYears(32L, List.of(2024))).thenReturn(Map.of());

        priceService.getPriceTrend(32L, 365, startDate, endDate);

        verify(priceRepository).findByProductIdAndDateRange(32L, startDate, endDate);
    }

    @Test
    void getPriceTrendRejectsReversedDateRange() {
        assertThrows(IllegalArgumentException.class, () ->
                priceService.getPriceTrend(
                        32L,
                        365,
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2025, 1, 1)));
    }

    @Test
    void getPriceYearsReturnsRecordedYearsWithoutArtificialLimit() {
        when(priceRepository.findPriceYearsByProductId(32L)).thenReturn(List.of(2026, 2012, 1998));

        assertEquals(List.of(2026, 2012, 1998), priceService.getPriceYears(32L));
    }

    @Test
    void getPriceTrendReturnsBudgetOnlyDateAxis() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 3);
        when(priceRepository.findByProductIdAndDateRange(32L, startDate, endDate)).thenReturn(List.of());
        when(annualBudgetService.getBudgetPriceMapByYears(32L, List.of(2026)))
                .thenReturn(Map.of(2026, new BigDecimal("8800.00")));

        List<PriceTrendDTO> result = priceService.getPriceTrend(32L, 30, startDate, endDate);

        assertEquals(3, result.size());
        assertEquals(LocalDate.of(2026, 6, 1), result.get(0).getDate());
        assertEquals(null, result.get(0).getCurrentPrice());
        assertEquals(new BigDecimal("8800.00"), result.get(0).getBudgetPrice());
        assertEquals(LocalDate.of(2026, 6, 3), result.get(2).getDate());
    }

    @Test
    void getPriceTrendUsesLatestCreatedPriceForDuplicateDate() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        Price older = new Price();
        older.setEffectiveDate(date);
        older.setCurrentPrice(new BigDecimal("8600.00"));
        older.setCreatedTime(LocalDateTime.of(2026, 6, 1, 9, 0));
        Price newer = new Price();
        newer.setEffectiveDate(date);
        newer.setCurrentPrice(new BigDecimal("8700.00"));
        newer.setCreatedTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        when(priceRepository.findByProductIdAndDateRange(32L, date, date)).thenReturn(List.of(older, newer));
        when(annualBudgetService.getBudgetPriceMapByYears(32L, List.of(2026))).thenReturn(Map.of());

        List<PriceTrendDTO> result = priceService.getPriceTrend(32L, 1, date, date);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("8700.00"), result.get(0).getCurrentPrice());
    }

    @Test
    void getPriceTrendSwitchesBudgetAcrossYears() {
        LocalDate startDate = LocalDate.of(2025, 12, 31);
        LocalDate endDate = LocalDate.of(2026, 1, 1);
        when(priceRepository.findByProductIdAndDateRange(32L, startDate, endDate)).thenReturn(List.of());
        when(annualBudgetService.getBudgetPriceMapByYears(32L, List.of(2025, 2026)))
                .thenReturn(Map.of(
                        2025, new BigDecimal("8100.00"),
                        2026, new BigDecimal("8800.00")
                ));

        List<PriceTrendDTO> result = priceService.getPriceTrend(32L, 2, startDate, endDate);

        assertEquals(new BigDecimal("8100.00"), result.get(0).getBudgetPrice());
        assertEquals(new BigDecimal("8800.00"), result.get(1).getBudgetPrice());
    }

    @Test
    void getPriceTrendRejectsOverlongDateRange() {
        assertThrows(IllegalArgumentException.class, () ->
                priceService.getPriceTrend(
                        32L,
                        365,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2026, 1, 7)));
    }

    @Test
    void getCurrentPriceUsesPriceEffectiveYearBudget() {
        Product product = new Product();
        product.setId(32L);
        Price price = new Price();
        price.setProduct(product);
        price.setEffectiveDate(LocalDate.of(2024, 6, 1));

        LocalDate today = LocalDate.now();
        when(priceRepository.findLatestPriceBeforeDate(32L, today)).thenReturn(Optional.of(price));
        when(annualBudgetService.getBudgetPrice(32L, today))
                .thenReturn(Optional.of(new BigDecimal("8800.00")));

        Optional<Price> result = priceService.getCurrentPriceByProductId(32L);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("8800.00"), result.get().getBudgetPrice());
        verify(annualBudgetService).getBudgetPrice(32L, today);
    }

    @Test
    void getPriceTrendCarriesLatestEffectivePriceAcrossDateRange() {
        LocalDate startDate = LocalDate.of(2026, 6, 10);
        LocalDate endDate = LocalDate.of(2026, 6, 12);
        Price previous = new Price();
        previous.setEffectiveDate(LocalDate.of(2026, 6, 8));
        previous.setCurrentPrice(new BigDecimal("8600.00"));
        previous.setCreatedTime(LocalDateTime.of(2026, 6, 8, 9, 0));

        when(priceRepository.findByProductIdAndDateRange(32L, startDate, endDate)).thenReturn(List.of());
        when(priceRepository.findLatestPriceBeforeDate(32L, startDate.minusDays(1))).thenReturn(Optional.of(previous));
        when(annualBudgetService.getBudgetPriceMapByYears(32L, List.of(2026))).thenReturn(Map.of());

        List<PriceTrendDTO> result = priceService.getPriceTrend(32L, 3, startDate, endDate);

        assertEquals(new BigDecimal("8600.00"), result.get(0).getCurrentPrice());
        assertEquals(new BigDecimal("8600.00"), result.get(2).getCurrentPrice());
    }

    @Test
    void getPriceByDateReturnsLatestEffectivePriceWithRequestedYearBudget() {
        LocalDate targetDate = LocalDate.of(2026, 6, 12);
        Product product = new Product();
        product.setId(32L);
        Price previous = new Price();
        previous.setProduct(product);
        previous.setEffectiveDate(LocalDate.of(2026, 6, 8));
        previous.setCurrentPrice(new BigDecimal("8600.00"));

        when(priceRepository.findLatestPriceBeforeDate(32L, targetDate)).thenReturn(Optional.of(previous));
        when(annualBudgetService.getBudgetPrice(32L, targetDate))
                .thenReturn(Optional.of(new BigDecimal("8800.00")));

        Optional<Price> result = priceService.getValidPriceByProductIdAndDate(32L, targetDate);

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("8600.00"), result.get().getCurrentPrice());
        assertEquals(new BigDecimal("8800.00"), result.get().getBudgetPrice());
    }
}
