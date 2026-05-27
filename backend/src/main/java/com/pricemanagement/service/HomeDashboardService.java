package com.pricemanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.dto.*;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductCategoryRepository;
import com.pricemanagement.repository.ProductRepository;
import com.pricemanagement.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页仪表盘服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeDashboardService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final PriceRepository priceRepository;
    private final SysDictRepository sysDictRepository;
    private final ObjectMapper objectMapper;

    private static final String CATEGORY_HOME_LAYOUT = "home_layout";
    private static final String CATEGORY_PRICE_ALERT = "price_alert";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取仪表盘数据
     */
    public HomeDashboardDTO getDashboardData(LocalDate date) {
        HomeDashboardDTO dashboard = new HomeDashboardDTO();

        // 获取摘要统计
        dashboard.setSummary(getSummaryStats(date));

        // 获取重点产品指标
        int featuredCount = getFeaturedProductCount();
        dashboard.setFeaturedProducts(getFeaturedProducts(date, featuredCount));

        // 获取价格预警
        dashboard.setAlerts(getPriceAlerts(date));

        // 获取趋势分析（默认30天）
        dashboard.setTrendAnalysis(getTrendAnalysis(date, 30));

        return dashboard;
    }

    /**
     * 获取首页产品列表排序树：启用分类 + 各分类启用产品。
     */
    public List<HomeProductOrderDTO> getProductOrder() {
        List<ProductCategory> categories = productCategoryRepository.findByStatusOrderBySortOrderAsc(CommonStatus.ACTIVE);
        List<Product> activeProducts = productRepository.findByStatus(CommonStatus.ACTIVE);

        Comparator<Product> productComparator = Comparator
                .comparing((Product product) -> product.getSortOrder() == null ? Integer.MAX_VALUE : product.getSortOrder())
                .thenComparing(Product::getName, Comparator.nullsLast(String::compareTo))
                .thenComparing(Product::getId);

        Map<Long, List<Product>> productsByCategory = activeProducts.stream()
                .filter(product -> product.getCategory() != null && product.getCategory().getId() != null)
                .collect(Collectors.groupingBy(product -> product.getCategory().getId()));

        List<HomeProductOrderDTO> groups = new ArrayList<>();
        for (ProductCategory category : categories) {
            List<HomeProductOrderDTO.ProductOrderItem> products = productsByCategory
                    .getOrDefault(category.getId(), Collections.emptyList())
                    .stream()
                    .sorted(productComparator)
                    .map(this::toProductOrderItem)
                    .collect(Collectors.toList());

            groups.add(new HomeProductOrderDTO(
                    new HomeProductOrderDTO.CategoryOrderItem(
                            category.getId(),
                            category.getName(),
                            category.getCode(),
                            category.getSortOrder(),
                            category.getStatus()
                    ),
                    null,
                    category.getName(),
                    products
            ));
        }

        List<HomeProductOrderDTO.ProductOrderItem> uncategorizedProducts = activeProducts.stream()
                .filter(product -> product.getCategory() == null || product.getCategory().getId() == null)
                .sorted(productComparator)
                .map(this::toProductOrderItem)
                .collect(Collectors.toList());

        if (!uncategorizedProducts.isEmpty()) {
            groups.add(new HomeProductOrderDTO(
                    null,
                    "uncategorized",
                    "未分类",
                    uncategorizedProducts
            ));
        }

        return groups;
    }

    /**
     * 获取摘要统计
     */
    public HomeSummaryDTO getSummaryStats(LocalDate date) {
        HomeSummaryDTO summary = new HomeSummaryDTO();

        // 产品总数（启用状态）
        List<Product> allProducts = productRepository.findByStatus(CommonStatus.ACTIVE);
        summary.setTotalProducts(allProducts.size());
        List<ProductCategory> activeCategories = productCategoryRepository.findByStatusOrderBySortOrderAsc(CommonStatus.ACTIVE);
        summary.setActiveCategoryCount(activeCategories.size());
        long coveredCategoryCount = allProducts.stream()
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .map(ProductCategory::getId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        summary.setCoveredCategoryCount((int) coveredCategoryCount);

        // 所选日期价格数据
        List<Price> todayPrices = priceRepository.findValidPricesByDate(date);
        int quotedProductCount = (int) todayPrices.stream()
                .filter(p -> p.getProduct() != null && p.getProduct().getId() != null)
                .map(p -> p.getProduct().getId())
                .distinct()
                .count();
        summary.setPriceUpdatedToday(quotedProductCount);

        // 前一日价格数据
        LocalDate prevDate = date.minusDays(1);
        List<Price> prevPrices = priceRepository.findValidPricesByDate(prevDate);

        // 计算涨跌统计
        Map<Long, Price> todayMap = todayPrices.stream()
                .filter(p -> p.getProduct() != null)
                .collect(Collectors.toMap(p -> p.getProduct().getId(), p -> p, (a, b) -> a));

        Map<Long, Price> prevMap = prevPrices.stream()
                .filter(p -> p.getProduct() != null)
                .collect(Collectors.toMap(p -> p.getProduct().getId(), p -> p, (a, b) -> a));

        int risingCount = 0;
        int fallingCount = 0;
        int flatCount = 0;
        double totalChange = 0;
        int changeCount = 0;

        for (Product product : allProducts) {
            Price today = todayMap.get(product.getId());
            Price prev = prevMap.get(product.getId());

            if (today != null && prev != null
                    && today.getCurrentPrice() != null
                    && prev.getCurrentPrice() != null) {

                BigDecimal diff = today.getCurrentPrice().subtract(prev.getCurrentPrice());
                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    risingCount++;
                    if (prev.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                        double percent = diff.divide(prev.getCurrentPrice(), 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal(100)).doubleValue();
                        totalChange += percent;
                        changeCount++;
                    }
                } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                    fallingCount++;
                    if (prev.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                        double percent = diff.divide(prev.getCurrentPrice(), 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal(100)).doubleValue();
                        totalChange += percent;
                        changeCount++;
                    }
                } else {
                    flatCount++;
                }
            }
        }

        summary.setRisingCount(risingCount);
        summary.setFallingCount(fallingCount);
        summary.setFlatCount(flatCount);
        summary.setChangedProductCount(risingCount + fallingCount);
        summary.setAvgPriceChange(changeCount > 0 ? totalChange / changeCount : 0);

        return summary;
    }

    /**
     * 获取重点产品指标
     */
    public List<ProductMetricDTO> getFeaturedProducts(LocalDate date, int count) {
        // 获取所有启用产品，筛选showOnHome
        List<Product> allProducts = productRepository.findByStatus(CommonStatus.ACTIVE);
        List<Product> featuredProducts = allProducts.stream()
                .filter(p -> Boolean.TRUE.equals(p.getShowOnHome()))
                .limit(count)
                .collect(Collectors.toList());

        LocalDate prevDate = date.minusDays(1);
        List<Price> todayPrices = priceRepository.findValidPricesByDate(date);
        List<Price> prevPrices = priceRepository.findValidPricesByDate(prevDate);

        Map<Long, Price> todayMap = todayPrices.stream()
                .filter(p -> p.getProduct() != null)
                .collect(Collectors.toMap(p -> p.getProduct().getId(), p -> p, (a, b) -> a));

        Map<Long, Price> prevMap = prevPrices.stream()
                .filter(p -> p.getProduct() != null)
                .collect(Collectors.toMap(p -> p.getProduct().getId(), p -> p, (a, b) -> a));

        return featuredProducts.stream()
                .map(product -> buildProductMetric(product, todayMap, prevMap, date))
                .collect(Collectors.toList());
    }

    /**
     * 获取价格预警
     */
    public List<PriceAlertDTO> getPriceAlerts(LocalDate date) {
        List<PriceAlertDTO> alerts = new ArrayList<>();

        // 获取预警规则配置
        List<SysDict> alertRules = sysDictRepository.findByCategoryOrderBySortOrderAsc(CATEGORY_PRICE_ALERT);

        for (SysDict rule : alertRules) {
            if (rule.getStatus() != CommonStatus.ACTIVE) {
                continue;
            }

            try {
                Map<String, Object> config = objectMapper.readValue(
                        rule.getExtraValue(), new TypeReference<Map<String, Object>>() {});

                String type = (String) config.get("type");
                String severity = (String) config.get("severity");

                if ("percentage".equals(type)) {
                    // 单日涨跌幅预警
                    double threshold = ((Number) config.get("threshold")).doubleValue();
                    String direction = (String) config.get("direction");

                    alerts.addAll(checkPercentageAlert(date, threshold, direction, severity, rule.getDictValue()));
                } else if ("consecutive".equals(type)) {
                    // 连续涨跌预警
                    int days = ((Number) config.get("days")).intValue();
                    String direction = (String) config.get("direction");

                    alerts.addAll(checkConsecutiveAlert(date, days, direction, severity, rule.getDictValue()));
                }
            } catch (Exception e) {
                log.warn("Failed to parse alert rule {}: {}", rule.getDictKey(), e.getMessage());
            }
        }

        return alerts;
    }

    /**
     * 获取趋势分析
     */
    public TrendAnalysisDTO getTrendAnalysis(LocalDate endDate, int days) {
        TrendAnalysisDTO trend = new TrendAnalysisDTO();
        trend.setDays(days);

        // 计算日期范围
        LocalDate startDate = endDate.minusDays(days - 1);
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            dates.add(startDate.plusDays(i).format(DATE_FORMATTER));
        }
        trend.setDates(dates);

        // 获取重点产品
        List<Product> allProducts = productRepository.findByStatus(CommonStatus.ACTIVE);
        List<Product> featuredProducts = allProducts.stream()
                .filter(p -> Boolean.TRUE.equals(p.getShowOnHome()))
                .collect(Collectors.toList());

        Map<Long, List<Double>> productTrends = new HashMap<>();
        List<Double> avgTrend = new ArrayList<>();

        for (String dateStr : dates) {
            LocalDate d = LocalDate.parse(dateStr, DATE_FORMATTER);
            List<Price> dayPrices = priceRepository.findValidPricesByDate(d);

            double daySum = 0;
            int dayCount = 0;

            for (Product product : featuredProducts) {
                List<Double> productPrices = productTrends.computeIfAbsent(product.getId(), k -> new ArrayList<>());

                Optional<Price> priceOpt = dayPrices.stream()
                        .filter(p -> p.getProduct() != null && p.getProduct().getId().equals(product.getId()))
                        .findFirst();

                if (priceOpt.isPresent() && priceOpt.get().getCurrentPrice() != null) {
                    productPrices.add(priceOpt.get().getCurrentPrice().doubleValue());
                    daySum += priceOpt.get().getCurrentPrice().doubleValue();
                    dayCount++;
                } else {
                    productPrices.add(null);
                }
            }

            avgTrend.add(dayCount > 0 ? daySum / dayCount : null);
        }

        trend.setProductTrends(productTrends);
        trend.setAvgTrend(avgTrend);
        trend.setRangeLabel(days + "日");

        return trend;
    }

    // ===== 私有方法 =====

    private ProductMetricDTO buildProductMetric(Product product, Map<Long, Price> todayMap,
                                                  Map<Long, Price> prevMap, LocalDate date) {
        ProductMetricDTO metric = new ProductMetricDTO();
        metric.setProductId(product.getId());
        metric.setProductName(product.getName());
        metric.setSpecs(product.getSpecs());
        metric.setOriginIds(product.getOriginIds());
        metric.setUnit(product.getUnit());
        metric.setFeatured(true);

        // 获取货币符号
        String currency = product.getCurrency() != null ? product.getCurrency() : "CNY";
        Optional<SysDict> currencyDict = sysDictRepository.findByCategoryAndDictKey("currency", currency);
        metric.setCurrencySymbol(currencyDict.map(SysDict::getExtraValue).orElse("¥"));

        Price today = todayMap.get(product.getId());
        Price prev = prevMap.get(product.getId());

        if (today != null && today.getCurrentPrice() != null) {
            metric.setCurrentPrice(today.getCurrentPrice().doubleValue());
            metric.setUnit(today.getUnit() != null ? today.getUnit() : product.getUnit());
            metric.setUpdateTime(today.getEffectiveDate() != null ? today.getEffectiveDate().toString() : null);
        }

        if (today != null && prev != null
                && today.getCurrentPrice() != null
                && prev.getCurrentPrice() != null) {

            BigDecimal diff = today.getCurrentPrice().subtract(prev.getCurrentPrice());
            metric.setPriceChange(diff.doubleValue());

            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                metric.setPriceDirection("up");
                if (prev.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                    double percent = diff.divide(prev.getCurrentPrice(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal(100)).doubleValue();
                    metric.setPriceChangePercent(percent);
                    metric.setFormattedChange("+" + formatPercent(percent));
                }
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                metric.setPriceDirection("down");
                if (prev.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {
                    double percent = Math.abs(diff.divide(prev.getCurrentPrice(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal(100)).doubleValue());
                    metric.setPriceChangePercent(-percent);
                    metric.setFormattedChange("-" + formatPercent(percent));
                }
            } else {
                metric.setPriceDirection("flat");
                metric.setPriceChangePercent(0.0);
                metric.setFormattedChange("0");
            }
        } else {
            metric.setPriceDirection("flat");
            metric.setFormattedChange("—");
        }

        return metric;
    }

    private HomeProductOrderDTO.ProductOrderItem toProductOrderItem(Product product) {
        Long categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
        return new HomeProductOrderDTO.ProductOrderItem(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getSpecs(),
                product.getOriginIds(),
                product.getSortOrder(),
                product.getShowOnHome(),
                product.getStatus(),
                product.getUnit(),
                product.getCurrency(),
                categoryId
        );
    }

    private List<PriceAlertDTO> checkPercentageAlert(LocalDate date, double threshold,
                                                       String direction, String severity, String alertName) {
        List<PriceAlertDTO> alerts = new ArrayList<>();

        LocalDate prevDate = date.minusDays(1);
        List<Price> todayPrices = priceRepository.findValidPricesByDate(date);
        List<Price> prevPrices = priceRepository.findValidPricesByDate(prevDate);

        Map<Long, Price> prevMap = prevPrices.stream()
                .filter(p -> p.getProduct() != null)
                .collect(Collectors.toMap(p -> p.getProduct().getId(), p -> p, (a, b) -> a));

        for (Price today : todayPrices) {
            if (today.getProduct() == null || today.getCurrentPrice() == null) continue;

            Price prev = prevMap.get(today.getProduct().getId());
            if (prev == null || prev.getCurrentPrice() == null) continue;

            BigDecimal diff = today.getCurrentPrice().subtract(prev.getCurrentPrice());
            if (prev.getCurrentPrice().compareTo(BigDecimal.ZERO) <= 0) continue;

            double percent = Math.abs(diff.divide(prev.getCurrentPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100)).doubleValue());

            if (percent >= threshold) {
                boolean matchDirection = "up".equals(direction) && diff.compareTo(BigDecimal.ZERO) > 0
                        || "down".equals(direction) && diff.compareTo(BigDecimal.ZERO) < 0;

                if (matchDirection) {
                    PriceAlertDTO alert = new PriceAlertDTO();
                    alert.setProductId(today.getProduct().getId());
                    alert.setProductName(today.getProduct().getName());
                    alert.setProductSpecs(today.getProduct().getSpecs());
                    alert.setAlertType("percentage");
                    alert.setAlertMessage(alertName + ": " + formatPercent(percent));
                    alert.setSeverity(severity);
                    alert.setCurrentValue(today.getCurrentPrice().doubleValue());
                    alert.setThreshold(threshold);
                    alert.setChangePercent(diff.compareTo(BigDecimal.ZERO) > 0 ? percent : -percent);
                    alerts.add(alert);
                }
            }
        }

        return alerts;
    }

    private List<PriceAlertDTO> checkConsecutiveAlert(LocalDate endDate, int days,
                                                        String direction, String severity, String alertName) {
        List<PriceAlertDTO> alerts = new ArrayList<>();

        List<Product> allProducts = productRepository.findByStatus(CommonStatus.ACTIVE);

        for (Product product : allProducts) {
            List<Double> prices = new ArrayList<>();
            boolean consecutive = true;

            for (int i = 0; i < days; i++) {
                LocalDate d = endDate.minusDays(i);
                List<Price> dayPrices = priceRepository.findValidPricesByDate(d);

                Optional<Price> priceOpt = dayPrices.stream()
                        .filter(p -> p.getProduct() != null && p.getProduct().getId().equals(product.getId()))
                        .findFirst();

                if (priceOpt.isPresent() && priceOpt.get().getCurrentPrice() != null) {
                    prices.add(priceOpt.get().getCurrentPrice().doubleValue());
                } else {
                    consecutive = false;
                    break;
                }
            }

            if (!consecutive || prices.size() < days) continue;

            // 检查连续涨跌
            boolean allMatch = true;
            for (int i = 1; i < prices.size(); i++) {
                double diff = prices.get(i - 1) - prices.get(i); // 从旧到新
                boolean isUp = diff > 0;
                boolean isDown = diff < 0;

                if ("up".equals(direction) && !isUp) allMatch = false;
                if ("down".equals(direction) && !isDown) allMatch = false;

                if (!allMatch) break;
            }

            if (allMatch) {
                double startPrice = prices.get(prices.size() - 1);
                double endPrice = prices.get(0);
                double totalChange = startPrice > 0 ? ((endPrice - startPrice) / startPrice) * 100 : 0;

                PriceAlertDTO alert = new PriceAlertDTO();
                alert.setProductId(product.getId());
                alert.setProductName(product.getName());
                alert.setProductSpecs(product.getSpecs());
                alert.setAlertType("consecutive");
                alert.setAlertMessage(alertName + ": 累计" + formatPercent(Math.abs(totalChange)));
                alert.setSeverity(severity);
                alert.setCurrentValue(endPrice);
                alert.setThreshold(days);
                alert.setChangePercent(totalChange);
                alerts.add(alert);
            }
        }

        return alerts;
    }

    private int getFeaturedProductCount() {
        Optional<SysDict> dict = sysDictRepository.findByCategoryAndDictKey(CATEGORY_HOME_LAYOUT, "featured_product_count");
        if (dict.isPresent()) {
            try {
                int count = Integer.parseInt(dict.get().getExtraValue());
                return Math.max(1, Math.min(count, 4));
            } catch (NumberFormatException e) {
                return 4;
            }
        }
        return 4;
    }

    private String formatPercent(double percent) {
        return String.format("%.1f%%", percent).replace(".0%", "%");
    }
}
