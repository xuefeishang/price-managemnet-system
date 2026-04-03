package com.pricemanagement.service;

import com.pricemanagement.entity.Origin;
import com.pricemanagement.repository.OriginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OriginService {

    private final OriginRepository originRepository;

    public List<Origin> getAllOrigins() {
        return originRepository.findAll();
    }

    public List<Origin> getActiveOrigins() {
        return originRepository.findByStatusOrderBySortOrderAsc(Origin.OriginStatus.ACTIVE);
    }

    public Optional<Origin> getOriginById(Long id) {
        return originRepository.findById(id);
    }

    public Optional<Origin> getOriginByCode(String code) {
        return originRepository.findByCode(code);
    }

    @Transactional
    public Origin createOrigin(Origin origin) {
        if (originRepository.existsByCode(origin.getCode())) {
            throw new IllegalArgumentException("产地编码已存在: " + origin.getCode());
        }
        Origin savedOrigin = originRepository.save(origin);
        log.info("Created origin: {}", savedOrigin.getName());
        return savedOrigin;
    }

    @Transactional
    public Origin updateOrigin(Long id, Origin origin) {
        Origin existingOrigin = originRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("产地不存在: " + id));

        if (origin.getName() != null) {
            existingOrigin.setName(origin.getName());
        }
        if (origin.getCode() != null && !origin.getCode().equals(existingOrigin.getCode())) {
            if (originRepository.existsByCode(origin.getCode())) {
                throw new IllegalArgumentException("产地编码已存在: " + origin.getCode());
            }
            existingOrigin.setCode(origin.getCode());
        }
        if (origin.getStatus() != null) {
            existingOrigin.setStatus(origin.getStatus());
        }
        if (origin.getSortOrder() != null) {
            existingOrigin.setSortOrder(origin.getSortOrder());
        }
        if (origin.getRemark() != null) {
            existingOrigin.setRemark(origin.getRemark());
        }

        Origin savedOrigin = originRepository.save(existingOrigin);
        log.info("Updated origin: {}", savedOrigin.getName());
        return savedOrigin;
    }

    @Transactional
    public void deleteOrigin(Long id) {
        if (!originRepository.existsById(id)) {
            throw new IllegalArgumentException("产地不存在: " + id);
        }
        originRepository.deleteById(id);
        log.info("Deleted origin with id: {}", id);
    }

    public boolean existsByCode(String code) {
        return originRepository.existsByCode(code);
    }
}
