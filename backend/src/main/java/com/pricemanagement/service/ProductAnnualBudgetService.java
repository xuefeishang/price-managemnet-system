package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.ProductAnnualBudgetDTO;
import com.pricemanagement.dto.ProductAnnualBudgetRequest;
import com.pricemanagement.dto.ProductAnnualBudgetSummaryDTO;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.ProductAnnualBudget;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductAnnualBudgetRepository;
import com.pricemanagement.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAnnualBudgetService {

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2999;

    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final ProductAnnualBudgetRepository budgetRepository;

    @Transactional(readOnly = true)
    public ProductAnnualBudgetSummaryDTO listBudgets(Integer year, String keyword, Long categoryId, CommonStatus status) {
        int budgetYear = normalizeYear(year);
        Specification<Product> spec = buildProductSpec(keyword, categoryId, status);
        List<Product> products = productRepository.findAll(
                spec,
                Sort.by(Sort.Order.asc("sortOrder").nullsLast(), Sort.Order.asc("name"))
        );

        List<Long> productIds = products.stream().map(Product::getId).filter(Objects::nonNull).toList();
        Map<Long, ProductAnnualBudget> budgetMap = budgetRepository.findByProductIdInAndBudgetYear(productIds, budgetYear).stream()
                .collect(Collectors.toMap(budget -> budget.getProduct().getId(), budget -> budget));
        Map<Long, BigDecimal> latestPriceMap = loadLatestPrices(productIds, LocalDate.now());

        List<ProductAnnualBudgetDTO> items = products.stream()
                .map(product -> ProductAnnualBudgetDTO.of(product, budgetYear, budgetMap.get(product.getId()), latestPriceMap.get(product.getId())))
                .toList();

        long configured = items.stream().filter(ProductAnnualBudgetDTO::isConfigured).count();
        return ProductAnnualBudgetSummaryDTO.builder()
                .budgetYear(budgetYear)
                .totalProducts(items.size())
                .configuredProducts(configured)
                .pendingProducts(items.size() - configured)
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductAnnualBudgetDTO getBudget(Long productId, Integer year) {
        if (productId == null) {
            throw new IllegalArgumentException("产品ID不能为空");
        }
        int budgetYear = normalizeYear(year);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));
        ProductAnnualBudget budget = budgetRepository.findByProductIdAndBudgetYear(productId, budgetYear).orElse(null);
        BigDecimal latestPrice = loadLatestPrices(List.of(productId), LocalDate.now()).get(productId);
        return ProductAnnualBudgetDTO.of(product, budgetYear, budget, latestPrice);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getBudgetPrice(Long productId, LocalDate date) {
        if (productId == null || date == null) return Optional.empty();
        return budgetRepository.findByProductIdAndBudgetYear(productId, date.getYear())
                .map(ProductAnnualBudget::getBudgetPrice);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getBudgetPriceMap(List<Long> productIds, LocalDate date) {
        if (productIds == null || productIds.isEmpty() || date == null) return Map.of();
        return budgetRepository.findByProductIdInAndBudgetYear(productIds, date.getYear()).stream()
                .filter(budget -> budget.getBudgetPrice() != null)
                .collect(Collectors.toMap(budget -> budget.getProduct().getId(), ProductAnnualBudget::getBudgetPrice));
    }

    @Transactional(readOnly = true)
    public Map<Integer, BigDecimal> getBudgetPriceMapByYears(Long productId, List<Integer> years) {
        if (productId == null || years == null || years.isEmpty()) return Map.of();
        Map<Integer, BigDecimal> result = new HashMap<>();
        for (Integer year : years.stream().filter(Objects::nonNull).distinct().toList()) {
            budgetRepository.findByProductIdAndBudgetYear(productId, year)
                    .map(ProductAnnualBudget::getBudgetPrice)
                    .ifPresent(value -> result.put(year, value));
        }
        return result;
    }

    @Transactional
    public ProductAnnualBudgetSummaryDTO saveBudgets(ProductAnnualBudgetRequest request, Long userId) {
        int budgetYear = normalizeYear(request != null ? request.getBudgetYear() : null);
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("预算明细不能为空");
        }

        Map<Long, Product> productMap = productRepository.findAllById(
                        request.getItems().stream().map(ProductAnnualBudgetRequest.Item::getProductId).filter(Objects::nonNull).toList()
                ).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        for (ProductAnnualBudgetRequest.Item item : request.getItems()) {
            if (item.getProductId() == null) {
                throw new IllegalArgumentException("产品ID不能为空");
            }
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                throw new IllegalArgumentException("产品不存在: " + item.getProductId());
            }
            if (item.getBudgetPrice() != null && item.getBudgetPrice().signum() < 0) {
                throw new IllegalArgumentException("预算价格不能为负数: " + product.getName());
            }

            ProductAnnualBudget budget = budgetRepository
                    .findByProductIdAndBudgetYear(item.getProductId(), budgetYear)
                    .orElseGet(() -> {
                        ProductAnnualBudget created = new ProductAnnualBudget();
                        created.setProduct(product);
                        created.setBudgetYear(budgetYear);
                        created.setCreatedBy(userId);
                        return created;
                    });

            if (item.getVersion() != null && budget.getVersion() != null && !item.getVersion().equals(budget.getVersion())) {
                throw new IllegalArgumentException("预算已被其他用户修改，请刷新后重试: " + product.getName());
            }
            budget.setBudgetPrice(item.getBudgetPrice());
            budget.setRemark(item.getRemark());
            budget.setUpdatedBy(userId);
            budgetRepository.save(budget);

        }

        log.info("Saved annual budgets: year={}, count={}", budgetYear, request.getItems().size());
        return listBudgets(budgetYear, null, null, CommonStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Integer> getBudgetYears(Long productId) {
        return budgetRepository.findBudgetYearsByProductId(productId);
    }

    private Map<Long, BigDecimal> loadLatestPrices(List<Long> productIds, LocalDate date) {
        if (productIds.isEmpty()) return Map.of();
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Price price : priceRepository.findLatestPricesBeforeDate(productIds, date)) {
            result.put(price.getProduct().getId(), price.getCurrentPrice());
        }
        return result;
    }

    private Specification<Product> buildProductSpec(String keyword, Long categoryId, CommonStatus status) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("code").as(String.class), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("specs").as(String.class), "")), like)
                ));
            }
            if (categoryId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
    }

    private int normalizeYear(Integer year) {
        int value = year != null ? year : LocalDate.now().getYear();
        if (value < MIN_YEAR || value > MAX_YEAR) {
            throw new IllegalArgumentException("预算年份需在 " + MIN_YEAR + "-" + MAX_YEAR + " 之间");
        }
        return value;
    }
}
