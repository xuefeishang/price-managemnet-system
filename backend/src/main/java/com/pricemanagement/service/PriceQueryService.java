package com.pricemanagement.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.constants.SystemConstants;
import com.pricemanagement.dto.PriceQueryExportExcelData;
import com.pricemanagement.dto.PriceQueryRowDTO;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductRepository;
import com.pricemanagement.repository.SysDictRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_EXPORT_ROWS = 10_000;

    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final SysDictRepository sysDictRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<PriceQueryRowDTO> query(LocalDate date, String keyword, Long categoryId, CommonStatus status,
                                        int page, int size, String sortBy, String sortDirection) {
        LocalDate targetDate = normalizeDate(date);
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size), buildSort(sortBy, sortDirection));
        Specification<Product> spec = buildProductSpec(keyword, categoryId, normalizeStatus(status));

        Page<Product> productPage = productRepository.findAll(spec, pageable);
        List<PriceQueryRowDTO> rows = buildRows(productPage.getContent(), targetDate);
        return new PageImpl<>(rows, pageable, productPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public void export(LocalDate date, String keyword, Long categoryId, CommonStatus status,
                       String sortBy, String sortDirection, HttpServletResponse response) throws IOException {
        LocalDate targetDate = normalizeDate(date);
        Specification<Product> spec = buildProductSpec(keyword, categoryId, normalizeStatus(status));
        long total = productRepository.count(spec);
        if (total > MAX_EXPORT_ROWS) {
            throw new IllegalArgumentException("导出数据超过 " + MAX_EXPORT_ROWS + " 行，请缩小筛选范围后重试");
        }

        List<Product> products = total == 0
                ? List.of()
                : productRepository.findAll(spec, PageRequest.of(0, (int) total, buildSort(sortBy, sortDirection))).getContent();
        Map<String, String> originNameMap = loadOriginNameMap();
        List<PriceQueryExportExcelData> excelData = buildRows(products, targetDate).stream()
                .map(row -> toExcelData(row, originNameMap))
                .toList();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("日常价格查询_" + targetDate, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), PriceQueryExportExcelData.class)
                .sheet("价格查询")
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .doWrite(excelData);
        log.info("Exported price query rows: date={}, total={}", targetDate, excelData.size());
    }

    private LocalDate normalizeDate(LocalDate date) {
        return date != null ? date : LocalDate.now().minusDays(1);
    }

    private CommonStatus normalizeStatus(CommonStatus status) {
        return status != null ? status : CommonStatus.ACTIVE;
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Sort buildSort(String sortBy, String sortDirection) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (sortBy == null || sortBy.isBlank()) {
            return Sort.by(Sort.Order.asc("sortOrder").nullsLast(), Sort.Order.asc("name"));
        }

        String property = switch (sortBy) {
            case "categoryName", "categoryId" -> "category.name";
            case "productName", "name" -> "name";
            case "specification", "specs" -> "specs";
            case "sortOrder", "updatedTime", "createdTime", "code" -> sortBy;
            default -> "name";
        };
        return Sort.by(new Sort.Order(direction, property).nullsLast());
    }

    private Specification<Product> buildProductSpec(String keyword, Long categoryId, CommonStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String keywordLike = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), keywordLike),
                        cb.like(cb.lower(cb.coalesce(root.get("specs").as(String.class), "")), keywordLike)
                ));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<PriceQueryRowDTO> buildRows(List<Product> products, LocalDate date) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).filter(Objects::nonNull).toList();
        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Price> currentPriceMap = toLatestCreatedPriceMap(
                priceRepository.findValidPricesByProductIdsAndDate(productIds, date));
        Map<Long, Price> yesterdayPriceMap = toLatestCreatedPriceMap(
                priceRepository.findValidPricesByProductIdsAndDate(productIds, date.minusDays(1)));
        Map<Long, BigDecimal> monthlyAverageMap = toMonthlyAverageMap(productIds, date);
        Map<Long, Price> latestPriceMap = toLatestEffectivePriceMap(
                priceRepository.findLatestPricesBeforeDate(productIds, date));

        return products.stream()
                .map(product -> toRow(product, date, currentPriceMap.get(product.getId()),
                        yesterdayPriceMap.get(product.getId()),
                        monthlyAverageMap.get(product.getId()),
                        latestPriceMap.get(product.getId())))
                .collect(Collectors.toList());
    }

    private Map<Long, BigDecimal> toMonthlyAverageMap(List<Long> productIds, LocalDate date) {
        LocalDate monthStart = date.withDayOfMonth(1);
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : priceRepository.findAveragePricesByProductIdsAndMonth(productIds, monthStart, date)) {
            Long productId = (Long) row[0];
            toBigDecimal(row[1]).ifPresent(value -> result.put(productId, value));
        }
        return result;
    }

    private Map<Long, Price> toLatestCreatedPriceMap(List<Price> prices) {
        Map<Long, Price> result = new HashMap<>();
        for (Price price : prices) {
            Long productId = price.getProduct().getId();
            Price existing = result.get(productId);
            if (existing == null || isCreatedAfter(price, existing)) {
                result.put(productId, price);
            }
        }
        return result;
    }

    private Map<Long, Price> toLatestEffectivePriceMap(List<Price> prices) {
        Map<Long, Price> result = new HashMap<>();
        for (Price price : prices) {
            Long productId = price.getProduct().getId();
            Price existing = result.get(productId);
            if (existing == null
                    || price.getEffectiveDate().isAfter(existing.getEffectiveDate())
                    || (price.getEffectiveDate().isEqual(existing.getEffectiveDate()) && isCreatedAfter(price, existing))) {
                result.put(productId, price);
            }
        }
        return result;
    }

    private boolean isCreatedAfter(Price candidate, Price existing) {
        if (candidate.getCreatedTime() == null) return false;
        if (existing.getCreatedTime() == null) return true;
        return candidate.getCreatedTime().isAfter(existing.getCreatedTime());
    }

    private Optional<BigDecimal> toBigDecimal(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof BigDecimal decimal) return Optional.of(decimal);
        if (value instanceof Number number) return Optional.of(new BigDecimal(number.toString()));
        return Optional.empty();
    }

    private PriceQueryRowDTO toRow(Product product, LocalDate date, Price currentPrice, Price yesterdayPrice,
                                   BigDecimal monthlyAveragePrice, Price latestPrice) {
        BigDecimal current = currentPrice != null ? currentPrice.getCurrentPrice() : null;
        BigDecimal yesterday = yesterdayPrice != null ? yesterdayPrice.getCurrentPrice() : null;
        BigDecimal changeAmount = calculateChangeAmount(current, yesterday);

        return PriceQueryRowDTO.builder()
                .productId(product.getId())
                .productName(product.getName())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .originIds(product.getOriginIds())
                .specification(product.getSpecs())
                .unit(firstNonBlank(
                        currentPrice != null ? currentPrice.getUnit() : null,
                        latestPrice != null ? latestPrice.getUnit() : null,
                        product.getUnit()))
                .currency(firstNonBlank(product.getCurrency(), SystemConstants.DEFAULT_CURRENCY))
                .effectiveDate(date)
                .currentPrice(current)
                .yesterdayPrice(yesterday)
                .changeAmount(changeAmount)
                .changePercent(calculateChangePercent(changeAmount, yesterday))
                .budgetPrice(firstNonNull(
                        currentPrice != null ? currentPrice.getBudgetPrice() : null,
                        product.getBudgetPrice(),
                        latestPrice != null ? latestPrice.getBudgetPrice() : null))
                .monthlyAveragePrice(monthlyAveragePrice)
                .latestPrice(latestPrice != null ? latestPrice.getCurrentPrice() : null)
                .hasPrice(current != null)
                .build();
    }

    private BigDecimal calculateChangeAmount(BigDecimal current, BigDecimal yesterday) {
        if (current == null || yesterday == null) return null;
        return current.subtract(yesterday);
    }

    private BigDecimal calculateChangePercent(BigDecimal changeAmount, BigDecimal yesterday) {
        if (changeAmount == null || yesterday == null || BigDecimal.ZERO.compareTo(yesterday) == 0) {
            return null;
        }
        return changeAmount.multiply(BigDecimal.valueOf(100))
                .divide(yesterday, 4, RoundingMode.HALF_UP);
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private Map<String, String> loadOriginNameMap() {
        return sysDictRepository.findByCategoryAndStatusOrderBySortOrderAsc("origin", CommonStatus.ACTIVE).stream()
                .collect(Collectors.toMap(SysDict::getDictKey, SysDict::getDictValue, (first, second) -> first));
    }

    private String resolveOriginNames(String originIds, Map<String, String> originNameMap) {
        if (originIds == null || originIds.isBlank()) {
            return null;
        }
        try {
            List<String> keys = objectMapper.readValue(originIds, new TypeReference<>() {});
            String names = keys.stream()
                    .map(originNameMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" / "));
            return names.isBlank() ? null : names;
        } catch (Exception ex) {
            log.warn("Failed to parse product origin ids for price query export: {}", originIds, ex);
            return null;
        }
    }

    private PriceQueryExportExcelData toExcelData(PriceQueryRowDTO row, Map<String, String> originNameMap) {
        PriceQueryExportExcelData data = new PriceQueryExportExcelData();
        data.setEffectiveDate(row.getEffectiveDate());
        data.setProductName(row.getProductName());
        data.setOriginName(resolveOriginNames(row.getOriginIds(), originNameMap));
        data.setSpecification(row.getSpecification());
        data.setUnit(row.getUnit());
        data.setCurrency(row.getCurrency());
        data.setCurrentPrice(row.getCurrentPrice());
        data.setYesterdayPrice(row.getYesterdayPrice());
        data.setChangeAmount(row.getChangeAmount());
        data.setChangePercent(row.getChangePercent() == null ? null : row.getChangePercent().stripTrailingZeros().toPlainString() + "%");
        data.setBudgetPrice(row.getBudgetPrice());
        data.setMonthlyAveragePrice(row.getMonthlyAveragePrice());
        data.setLatestPrice(row.getLatestPrice());
        data.setHasPrice(Boolean.TRUE.equals(row.getHasPrice()) ? "是" : "否");
        return data;
    }
}
