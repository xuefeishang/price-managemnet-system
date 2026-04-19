
package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.entity.ApprovalRequest;
import com.pricemanagement.entity.ApprovalWorkflow;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.PriceHistory;
import com.pricemanagement.entity.Product;
import com.pricemanagement.repository.ApprovalRequestRepository;
import com.pricemanagement.repository.ApprovalWorkflowRepository;
import com.pricemanagement.repository.PriceHistoryRepository;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.pricemanagement.dto.PriceWithStatsDTO;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final PriceRepository priceRepository;
    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalRequestRepository requestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<PriceHistory> getProductPriceHistory(Long productId) {
        List<PriceHistory> historyList = priceHistoryRepository.findByProductIdOrderByChangedTimeDesc(productId);
        log.debug("Found {} price history records for product id: {}", historyList.size(), productId);
        return historyList;
    }

    public Optional<Price> getPriceById(Long id) {
        return priceRepository.findById(id);
    }

    public Optional<Price> getCurrentPriceByProductId(Long productId) {
        return priceRepository.findFirstByProductIdOrderByCreatedTimeDesc(productId);
    }

    /**
     * 获取指定日期有效的所有价格
     */
    public List<Price> getValidPricesByDate(LocalDate date) {
        return priceRepository.findValidPricesByDate(date);
    }

    /**
     * 获取某产品在指定日期有效的价格
     */
    public Optional<Price> getValidPriceByProductIdAndDate(Long productId, LocalDate date) {
        return priceRepository.findValidPriceByProductIdAndDate(productId, date);
    }

    /**
     * 获取某产品昨日的价格
     */
    public Optional<Price> getYesterdayPriceByProductId(Long productId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return priceRepository.findValidPriceByProductIdAndDate(productId, yesterday);
    }

    /**
     * 获取指定日期有效的所有价格（带昨日价格和月均价）
     * 覆盖所有活跃产品：没有当日价格的产品也返回，使用最近一次价格作为继承价格
     */
    public List<PriceWithStatsDTO> getValidPricesWithStatsByDate(LocalDate date) {
        // 1. 获取所有活跃产品
        List<Product> activeProducts = productRepository.findByStatus(Product.ProductStatus.ACTIVE);
        if (activeProducts.isEmpty()) {
            return List.of();
        }

        List<Long> allProductIds = activeProducts.stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        // 2. 查询当日有价格记录的产品
        List<Price> todayPrices = priceRepository.findValidPricesByDate(date);
        Map<Long, Price> todayPriceMap = new HashMap<>();
        for (Price p : todayPrices) {
            todayPriceMap.put(p.getProduct().getId(), p);
        }

        // 3. 批量查询昨日价格
        LocalDate yesterday = date.minusDays(1);
        List<Price> yesterdayPrices = priceRepository.findValidPricesByProductIdsAndDate(allProductIds, yesterday);
        Map<Long, Price> yesterdayPriceMap = new HashMap<>();
        for (Price p : yesterdayPrices) {
            yesterdayPriceMap.put(p.getProduct().getId(), p);
        }

        // 4. 对于没有昨日价格的产品，查找更早的最近价格作为昨日价格
        // （即使当天有价格，如果昨天没有价格，也需要找到上一次有价格的日期作为"昨日价格"）
        Set<Long> productsNeedingLatestBeforeYesterday = allProductIds.stream()
                .filter(pid -> !yesterdayPriceMap.containsKey(pid))
                .collect(Collectors.toSet());

        if (!productsNeedingLatestBeforeYesterday.isEmpty()) {
            List<Price> latestBeforeYesterday = priceRepository.findLatestPricesBeforeDate(
                    new ArrayList<>(productsNeedingLatestBeforeYesterday), yesterday);
            for (Price p : latestBeforeYesterday) {
                if (!yesterdayPriceMap.containsKey(p.getProduct().getId())) {
                    yesterdayPriceMap.put(p.getProduct().getId(), p);
                }
            }
        }

        // 5. 批量查询月均价
        LocalDate monthStart = date.withDayOfMonth(1);
        List<Object[]> avgResults = priceRepository.findAveragePricesByProductIdsAndMonth(allProductIds, monthStart, date);
        Map<Long, BigDecimal> monthlyAvgMap = new HashMap<>();
        for (Object[] row : avgResults) {
            Long productId = (Long) row[0];
            Object avgObj = row[1];
            BigDecimal avg;
            if (avgObj instanceof BigDecimal) {
                avg = (BigDecimal) avgObj;
            } else if (avgObj instanceof Double) {
                avg = BigDecimal.valueOf((Double) avgObj);
            } else if (avgObj instanceof Number) {
                avg = new BigDecimal(avgObj.toString());
            } else {
                avg = null;
            }
            monthlyAvgMap.put(productId, avg);
        }

        // 6. 对于没有当日价格的产品，查找最近的价格作为继承价格
        Set<Long> productsNeedingInherited = allProductIds.stream()
                .filter(pid -> !todayPriceMap.containsKey(pid))
                .collect(Collectors.toSet());

        Map<Long, Price> inheritedPriceMap = new HashMap<>();
        if (!productsNeedingInherited.isEmpty()) {
            List<Price> latestPrices = priceRepository.findLatestPricesBeforeDate(
                    new ArrayList<>(productsNeedingInherited), date);
            for (Price p : latestPrices) {
                inheritedPriceMap.put(p.getProduct().getId(), p);
            }
        }

        // 7. 组装结果：覆盖所有活跃产品
        return activeProducts.stream()
                .map(product -> {
                    Long productId = product.getId();
                    Price todayPrice = todayPriceMap.get(productId);
                    Price yp = yesterdayPriceMap.get(productId);
                    BigDecimal avg = monthlyAvgMap.get(productId);

                    // 继承价格：当天没有价格时取最近一次价格
                    BigDecimal inheritedPrice = null;
                    if (todayPrice == null && inheritedPriceMap.containsKey(productId)) {
                        inheritedPrice = inheritedPriceMap.get(productId).getCurrentPrice();
                    }

                    if (todayPrice != null) {
                        // 当天有价格记录
                        return new PriceWithStatsDTO(todayPrice, yp, avg, null);
                    } else {
                        // 当天没有价格记录，创建一个空价格对象关联产品，附带继承价格
                        Price emptyPrice = new Price();
                        emptyPrice.setProduct(product);
                        emptyPrice.setEffectiveDate(date);
                        emptyPrice.setUnit(product.getUnit());
                        return new PriceWithStatsDTO(emptyPrice, yp, avg, inheritedPrice);
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取某产品当月的平均价格
     */
    public Double getMonthlyAveragePriceByProductId(Long productId) {
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        return priceRepository.findAveragePriceByProductIdAndMonth(productId, monthStart, now);
    }

    /**
     * 检查价格变更审批流是否启用
     */
    public boolean isPriceApprovalEnabled() {
        return workflowRepository.findByWorkflowTypeAndIsActiveTrue(ApprovalWorkflow.WorkflowType.PRICE_CHANGE).isPresent();
    }

    @Transactional
    public Price addProductPrice(Long productId, Price price, Long applicantId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + productId));

        price.setProduct(product);

        // 检查价格变更审批流是否启用
        Optional<ApprovalWorkflow> workflowOpt = workflowRepository.findByWorkflowTypeAndIsActiveTrue(ApprovalWorkflow.WorkflowType.PRICE_CHANGE);

        if (workflowOpt.isPresent()) {
            // 审批流启用，创建审批请求
            ApprovalWorkflow workflow = workflowOpt.get();

            // 序列化变更数据
            Map<String, Object> changeData = new HashMap<>();
            changeData.put("productId", productId);
            changeData.put("originalPrice", price.getOriginalPrice());
            changeData.put("currentPrice", price.getCurrentPrice());
            changeData.put("costPrice", price.getCostPrice());
            changeData.put("effectiveDate", price.getEffectiveDate());
            changeData.put("expiryDate", price.getExpiryDate());
            changeData.put("unit", price.getUnit());
            changeData.put("priceSpec", price.getPriceSpec());
            changeData.put("action", "CREATE");

            ApprovalRequest request = new ApprovalRequest();
            request.setWorkflowId(workflow.getId());
            request.setBusinessType(ApprovalRequest.BusinessType.PRICE);
            request.setBusinessId(productId);
            request.setStatus(ApprovalRequest.RequestStatus.PENDING);
            request.setApplicantId(applicantId);

            try {
                request.setRequestData(objectMapper.writeValueAsString(changeData));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize price change data", e);
            }

            // 保存审批请求
            ApprovalRequest savedRequest = requestRepository.save(request);
            log.info("Created approval request for price change: requestId={}, productId={}", savedRequest.getId(), productId);

            // 返回一个带有待审批标记的价格对象（不直接保存）
            price.setId(savedRequest.getId());
            price.setRemark("PENDING_APPROVAL");
            return price;
        }

        // 审批流未启用，直接保存价格
        return doSavePrice(product, price, null);
    }

    /**
     * 内部方法：真正保存价格
     * 每个产品每天只有一条价格记录，各日期完全独立
     */
    @Transactional
    public Price doSavePrice(Product product, Price price, BigDecimal oldPriceValue) {
        LocalDate effectiveDate = price.getEffectiveDate();
        // 按产品ID+生效日期精确查找已有记录
        Optional<Price> existingPrice = effectiveDate != null
                ? priceRepository.findByProductIdAndEffectiveDate(product.getId(), effectiveDate)
                : Optional.empty();

        Price savedPrice;
        BigDecimal oldPrice = oldPriceValue;

        if (existingPrice.isPresent()) {
            // 已有该日期的价格记录，更新价格字段
            Price existing = existingPrice.get();
            if (oldPrice == null) {
                oldPrice = existing.getCurrentPrice();
            }
            existing.setOriginalPrice(price.getOriginalPrice());
            existing.setCurrentPrice(price.getCurrentPrice());
            existing.setCostPrice(price.getCostPrice());
            existing.setUnit(price.getUnit());
            existing.setPriceSpec(price.getPriceSpec());
            savedPrice = priceRepository.save(existing);
            log.debug("Updated existing price for product: {} on date: {}", product.getName(), effectiveDate);
        } else {
            // 该日期没有价格记录，新建一条
            if (oldPrice == null) {
                Optional<Price> lastPrice = priceRepository.findFirstByProductIdOrderByCreatedTimeDesc(product.getId());
                oldPrice = lastPrice.map(Price::getCurrentPrice).orElse(null);
            }

            savedPrice = priceRepository.save(price);
            log.debug("Added new price for product: {} on date: {}", product.getName(), effectiveDate);
        }

        // 记录价格历史
        PriceHistory history = new PriceHistory();
        history.setPriceId(savedPrice.getId());
        history.setProductId(product.getId());
        history.setOldPrice(oldPrice);
        history.setNewPrice(savedPrice.getCurrentPrice());
        history.setChangeType(oldPrice == null ? PriceHistory.ChangeType.CREATE : PriceHistory.ChangeType.UPDATE);
        history.setRemark(oldPrice == null ? "创建产品价格" : "更新产品价格");
        priceHistoryRepository.save(history);

        // 同步更新产品售价（仅当价格生效日期为今天或之后时才同步）
        if (savedPrice.getCurrentPrice() != null && savedPrice.getEffectiveDate() != null
                && !savedPrice.getEffectiveDate().isBefore(LocalDate.now())) {
            product.setSellingPrice(savedPrice.getCurrentPrice());
            productRepository.save(product);
        }

        return savedPrice;
    }

    @Transactional
    public Price updatePrice(Long id, Price price, Long applicantId) {
        Price existingPrice = priceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("价格记录不存在: " + id));

        // 检查价格变更审批流是否启用
        Optional<ApprovalWorkflow> workflowOpt = workflowRepository.findByWorkflowTypeAndIsActiveTrue(ApprovalWorkflow.WorkflowType.PRICE_CHANGE);

        if (workflowOpt.isPresent()) {
            // 审批流启用，创建审批请求
            ApprovalWorkflow workflow = workflowOpt.get();

            // 序列化变更数据
            Map<String, Object> changeData = new HashMap<>();
            changeData.put("priceId", id);
            changeData.put("productId", existingPrice.getProduct().getId());
            changeData.put("originalPrice", price.getOriginalPrice() != null ? price.getOriginalPrice() : existingPrice.getOriginalPrice());
            changeData.put("currentPrice", price.getCurrentPrice() != null ? price.getCurrentPrice() : existingPrice.getCurrentPrice());
            changeData.put("costPrice", price.getCostPrice() != null ? price.getCostPrice() : existingPrice.getCostPrice());
            changeData.put("effectiveDate", price.getEffectiveDate() != null ? price.getEffectiveDate() : existingPrice.getEffectiveDate());
            changeData.put("expiryDate", price.getExpiryDate() != null ? price.getExpiryDate() : existingPrice.getExpiryDate());
            changeData.put("unit", price.getUnit() != null ? price.getUnit() : existingPrice.getUnit());
            changeData.put("priceSpec", price.getPriceSpec() != null ? price.getPriceSpec() : existingPrice.getPriceSpec());
            changeData.put("action", "UPDATE");

            ApprovalRequest request = new ApprovalRequest();
            request.setWorkflowId(workflow.getId());
            request.setBusinessType(ApprovalRequest.BusinessType.PRICE);
            request.setBusinessId(id);
            request.setStatus(ApprovalRequest.RequestStatus.PENDING);
            request.setApplicantId(applicantId);

            try {
                request.setRequestData(objectMapper.writeValueAsString(changeData));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize price change data", e);
            }

            ApprovalRequest savedRequest = requestRepository.save(request);
            log.info("Created approval request for price update: requestId={}, priceId={}", savedRequest.getId(), id);

            // 返回带有待审批标记的价格对象
            existingPrice.setRemark("PENDING_APPROVAL");
            return existingPrice;
        }

        // 审批流未启用，直接更新价格（仅更新价格字段，不修改 effectiveDate）
        BigDecimal oldPrice = existingPrice.getCurrentPrice();

        if (price.getOriginalPrice() != null) {
            existingPrice.setOriginalPrice(price.getOriginalPrice());
        }
        if (price.getCurrentPrice() != null) {
            existingPrice.setCurrentPrice(price.getCurrentPrice());
        }
        if (price.getCostPrice() != null) {
            existingPrice.setCostPrice(price.getCostPrice());
        }
        if (price.getUnit() != null) {
            existingPrice.setUnit(price.getUnit());
        }
        if (price.getPriceSpec() != null) {
            existingPrice.setPriceSpec(price.getPriceSpec());
        }

        Price updatedPrice = priceRepository.save(existingPrice);
        log.debug("Updated price with id: {}", id);

        if (!Objects.equals(oldPrice, updatedPrice.getCurrentPrice())) {
            PriceHistory history = new PriceHistory();
            history.setPriceId(updatedPrice.getId());
            history.setProductId(updatedPrice.getProduct().getId());
            history.setOldPrice(oldPrice);
            history.setNewPrice(updatedPrice.getCurrentPrice());
            history.setChangeType(PriceHistory.ChangeType.UPDATE);
            history.setRemark("更新产品价格");
            priceHistoryRepository.save(history);
            log.debug("Created price history for price change");
        }

        // 同步更新产品售价（仅当价格生效日期为今天或之后时才同步）
        if (updatedPrice.getCurrentPrice() != null && updatedPrice.getEffectiveDate() != null
                && !updatedPrice.getEffectiveDate().isBefore(LocalDate.now())) {
            existingPrice.getProduct().setSellingPrice(updatedPrice.getCurrentPrice());
            productRepository.save(existingPrice.getProduct());
        }

        return updatedPrice;
    }
}

