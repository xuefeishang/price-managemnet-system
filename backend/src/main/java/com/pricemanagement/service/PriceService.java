
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
     */
    public List<PriceWithStatsDTO> getValidPricesWithStatsByDate(LocalDate date) {
        List<Price> prices = priceRepository.findValidPricesByDate(date);

        if (prices.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = prices.stream()
                .map(p -> p.getProduct().getId())
                .distinct()
                .collect(Collectors.toList());

        LocalDate yesterday = date.minusDays(1);

        // 批量查询昨日价格
        List<Price> yesterdayPrices = priceRepository.findValidPricesByProductIdsAndDate(productIds, yesterday);
        Map<Long, Price> yesterdayPriceMap = new HashMap<>();
        for (Price p : yesterdayPrices) {
            yesterdayPriceMap.put(p.getProduct().getId(), p);
        }

        // 批量查询月均价
        LocalDate monthStart = date.withDayOfMonth(1);
        List<Object[]> avgResults = priceRepository.findAveragePricesByProductIdsAndMonth(productIds, monthStart, date);
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

        // 组装结果
        return prices.stream()
                .map(price -> {
                    Long productId = price.getProduct().getId();
                    Price yp = yesterdayPriceMap.get(productId);
                    BigDecimal avg = monthlyAvgMap.get(productId);
                    return new PriceWithStatsDTO(price, yp, avg);
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
     */
    @Transactional
    public Price doSavePrice(Product product, Price price, BigDecimal oldPriceValue) {
        LocalDate effectiveDate = price.getEffectiveDate();
        Optional<Price> existingPrice = effectiveDate != null
                ? priceRepository.findValidPriceByProductIdAndDate(product.getId(), effectiveDate)
                : Optional.empty();

        Price savedPrice;
        BigDecimal oldPrice = oldPriceValue;

        if (existingPrice.isPresent()) {
            Price existing = existingPrice.get();
            if (oldPrice == null) {
                oldPrice = existing.getCurrentPrice();
            }
            existing.setOriginalPrice(price.getOriginalPrice());
            existing.setCurrentPrice(price.getCurrentPrice());
            existing.setCostPrice(price.getCostPrice());
            existing.setUnit(price.getUnit());
            existing.setPriceSpec(price.getPriceSpec());
            if (price.getExpiryDate() != null) {
                existing.setExpiryDate(price.getExpiryDate());
            }
            savedPrice = priceRepository.save(existing);
            log.debug("Updated existing price for product: {}", product.getName());
        } else {
            if (oldPrice == null) {
                Optional<Price> lastPrice = priceRepository.findFirstByProductIdOrderByCreatedTimeDesc(product.getId());
                oldPrice = lastPrice.map(Price::getCurrentPrice).orElse(null);
            }

            if (effectiveDate != null) {
                Optional<Price> lastPrice = priceRepository.findFirstByProductIdOrderByCreatedTimeDesc(product.getId());
                if (lastPrice.isPresent()) {
                    Price last = lastPrice.get();
                    if (last.getExpiryDate() == null || !last.getExpiryDate().isBefore(effectiveDate)) {
                        last.setExpiryDate(effectiveDate.minusDays(1));
                        priceRepository.save(last);
                    }
                }
            }

            savedPrice = priceRepository.save(price);
            log.debug("Added new price for product: {}", product.getName());
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

        // 同步更新产品售价
        if (savedPrice.getCurrentPrice() != null) {
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

        // 审批流未启用，直接更新价格
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
        if (price.getEffectiveDate() != null) {
            existingPrice.setEffectiveDate(price.getEffectiveDate());
        }
        if (price.getExpiryDate() != null) {
            existingPrice.setExpiryDate(price.getExpiryDate());
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

        // 同步更新产品售价
        if (updatedPrice.getCurrentPrice() != null) {
            existingPrice.getProduct().setSellingPrice(updatedPrice.getCurrentPrice());
            productRepository.save(existingPrice.getProduct());
        }

        return updatedPrice;
    }
}

