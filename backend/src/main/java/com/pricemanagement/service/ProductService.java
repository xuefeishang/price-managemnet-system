
package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.entity.ApprovalRequest;
import com.pricemanagement.entity.ApprovalWorkflow;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.ProductCategory;
import com.pricemanagement.repository.ApprovalRequestRepository;
import com.pricemanagement.repository.ApprovalWorkflowRepository;
import com.pricemanagement.repository.ProductCategoryRepository;
import com.pricemanagement.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalRequestRepository requestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Page<Product> getProducts(int page, int size, String keyword, Long categoryId,
                                     Product.ProductStatus status, String sortBy, String sortDirection) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortBy != null ? sortBy : "id");
        Pageable pageable = PageRequest.of(page, size, sort);

        // 优先按关键字搜索
        if (keyword != null && !keyword.isBlank()) {
            Page<Product> products = productRepository.findByNameContaining(keyword, pageable);
            log.debug("Found {} products matching keyword: {}", products.getTotalElements(), keyword);
            return products;
        }

        // 按分类和状态筛选
        if (categoryId != null && status != null) {
            return productRepository.findByCategoryIdAndStatus(categoryId, status, pageable);
        } else if (categoryId != null) {
            return productRepository.findByCategoryId(categoryId, pageable);
        } else if (status != null) {
            return productRepository.findByStatus(status, pageable);
        }

        // 无筛选条件
        Page<Product> products = productRepository.findAll(pageable);
        log.debug("Found {} products", products.getTotalElements());
        return products;
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByStatus(Product.ProductStatus.ACTIVE);
    }

    /**
     * 检查产品创建审批流是否启用
     */
    public boolean isProductApprovalEnabled() {
        return workflowRepository.findByWorkflowTypeAndIsActiveTrue(ApprovalWorkflow.WorkflowType.PRODUCT_CREATE).isPresent();
    }

    @Transactional
    public Product createProduct(Product product, Long applicantId) {
        // 处理分类ID转换
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(product.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("分类不存在: " + product.getCategoryId()));
            product.setCategory(category);
        }

        // 处理产地和客户ID列表
        // originIds 和 customerIds 前端传递时已经是 JSON 字符串格式

        // 检查产品创建审批流是否启用
        Optional<ApprovalWorkflow> workflowOpt = workflowRepository.findByWorkflowTypeAndIsActiveTrue(ApprovalWorkflow.WorkflowType.PRODUCT_CREATE);

        if (workflowOpt.isPresent()) {
            // 审批流启用，创建审批请求
            ApprovalWorkflow workflow = workflowOpt.get();

            // 序列化变更数据
            Map<String, Object> changeData = buildProductChangeData(product, "CREATE");

            ApprovalRequest request = new ApprovalRequest();
            request.setWorkflowId(workflow.getId());
            request.setBusinessType(ApprovalRequest.BusinessType.PRODUCT);
            request.setBusinessId(0L); // 临时值，审批通过后会被替换为真实产品ID
            request.setStatus(ApprovalRequest.RequestStatus.PENDING);
            request.setApplicantId(applicantId);

            try {
                request.setRequestData(objectMapper.writeValueAsString(changeData));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize product change data", e);
            }

            ApprovalRequest savedRequest = requestRepository.save(request);
            log.info("Created approval request for product creation: requestId={}", savedRequest.getId());

            // 返回带有待审批标记的产品对象
            product.setId(savedRequest.getId());
            product.setRemark("PENDING_APPROVAL");
            return product;
        }

        // 审批流未启用，直接保存产品
        Product savedProduct = productRepository.save(product);
        log.info("Created product: {}", savedProduct.getName());
        return savedProduct;
    }

    @Transactional
    public Product updateProduct(Long id, Product product) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + id));

        if (product.getName() != null) {
            existingProduct.setName(product.getName());
        }
        if (product.getSellingPrice() != null) {
            existingProduct.setSellingPrice(product.getSellingPrice());
        }
        // 优先使用categoryId转换，其次使用category对象
        if (product.getCategoryId() != null) {
            ProductCategory category = categoryRepository.findById(product.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("分类不存在: " + product.getCategoryId()));
            existingProduct.setCategory(category);
        } else if (product.getCategory() != null) {
            existingProduct.setCategory(product.getCategory());
        }
        if (product.getStatus() != null) {
            existingProduct.setStatus(product.getStatus());
        }
        if (product.getDescription() != null) {
            existingProduct.setDescription(product.getDescription());
        }
        if (product.getSpecs() != null) {
            existingProduct.setSpecs(product.getSpecs());
        }
        if (product.getImageUrl() != null) {
            existingProduct.setImageUrl(product.getImageUrl());
        }
        if (product.getOriginIds() != null) {
            existingProduct.setOriginIds(product.getOriginIds());
        }
        if (product.getCustomerIds() != null) {
            existingProduct.setCustomerIds(product.getCustomerIds());
        }
        if (product.getRemark() != null) {
            existingProduct.setRemark(product.getRemark());
        }
        if (product.getUnit() != null) {
            existingProduct.setUnit(product.getUnit());
        }
        if (product.getShowOnHome() != null) {
            existingProduct.setShowOnHome(product.getShowOnHome());
        }
        if (product.getCurrency() != null) {
            existingProduct.setCurrency(product.getCurrency());
        }
        if (product.getSortOrder() != null) {
            existingProduct.setSortOrder(product.getSortOrder());
        }

        Product savedProduct = productRepository.save(existingProduct);
        log.info("Updated product: {}", savedProduct.getName());
        return savedProduct;
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("产品不存在: " + id);
        }
        productRepository.deleteById(id);
        log.info("Deleted product with id: {}", id);
    }

    @Transactional
    public void batchUpdateSort(List<java.util.Map<String, Object>> items) {
        for (java.util.Map<String, Object> item : items) {
            Long id = ((Number) item.get("id")).longValue();
            Integer sortOrder = ((Number) item.get("sortOrder")).intValue();
            productRepository.findById(id).ifPresent(product -> {
                product.setSortOrder(sortOrder);
                productRepository.save(product);
            });
        }
        log.info("Batch updated sort order for {} products", items.size());
    }

    private Map<String, Object> buildProductChangeData(Product product, String action) {
        Map<String, Object> changeData = new HashMap<>();
        changeData.put("name", product.getName());
        changeData.put("categoryId", product.getCategoryId());
        changeData.put("status", product.getStatus() != null ? product.getStatus().name() : "ACTIVE");
        changeData.put("description", product.getDescription());
        changeData.put("specs", product.getSpecs());
        changeData.put("imageUrl", product.getImageUrl());
        changeData.put("originIds", product.getOriginIds());
        changeData.put("customerIds", product.getCustomerIds());
        changeData.put("remark", product.getRemark());
        changeData.put("action", action);
        return changeData;
    }
}

