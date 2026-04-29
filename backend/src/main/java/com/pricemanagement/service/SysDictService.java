package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictRepository sysDictRepository;

    public List<SysDict> getAllDicts() {
        return sysDictRepository.findAllByOrderByCategoryAscSortOrderAsc();
    }

    public List<SysDict> getDictsByCategory(String category) {
        return sysDictRepository.findByCategoryOrderBySortOrderAsc(category);
    }

    public List<SysDict> getActiveDictsByCategory(String category) {
        return sysDictRepository.findByCategoryAndStatusOrderBySortOrderAsc(category, CommonStatus.ACTIVE);
    }

    public Optional<SysDict> getDictById(Long id) {
        return sysDictRepository.findById(id);
    }

    public Optional<SysDict> getDictByCategoryAndKey(String category, String dictKey) {
        return sysDictRepository.findByCategoryAndDictKey(category, dictKey);
    }

    public List<String> getCategories() {
        return sysDictRepository.findDistinctCategoryByStatus(CommonStatus.ACTIVE);
    }

    public List<String> getAllCategories() {
        return sysDictRepository.findAllDistinctCategory();
    }

    @Transactional
    public SysDict createDict(SysDict sysDict) {
        if (sysDictRepository.existsByCategoryAndDictKey(sysDict.getCategory(), sysDict.getDictKey())) {
            throw new IllegalArgumentException("字典项已存在: " + sysDict.getCategory() + " - " + sysDict.getDictKey());
        }
        SysDict saved = sysDictRepository.save(sysDict);
        log.info("Created dict: {} - {}", sysDict.getCategory(), sysDict.getDictKey());
        return saved;
    }

    @Transactional
    public SysDict updateDict(Long id, SysDict sysDict) {
        SysDict existing = sysDictRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("字典项不存在: " + id));

        if (sysDict.getCategory() != null) {
            existing.setCategory(sysDict.getCategory());
        }
        if (sysDict.getDictKey() != null) {
            // 检查新的 category+key 是否与其他记录冲突
            if (!sysDict.getDictKey().equals(existing.getDictKey()) ||
                !sysDict.getCategory().equals(existing.getCategory())) {
                String category = sysDict.getCategory() != null ? sysDict.getCategory() : existing.getCategory();
                if (sysDictRepository.existsByCategoryAndDictKey(category, sysDict.getDictKey())) {
                    throw new IllegalArgumentException("字典项已存在: " + category + " - " + sysDict.getDictKey());
                }
            }
            existing.setDictKey(sysDict.getDictKey());
        }
        if (sysDict.getDictValue() != null) {
            existing.setDictValue(sysDict.getDictValue());
        }
        if (sysDict.getExtraValue() != null) {
            existing.setExtraValue(sysDict.getExtraValue());
        }
        if (sysDict.getSortOrder() != null) {
            existing.setSortOrder(sysDict.getSortOrder());
        }
        if (sysDict.getStatus() != null) {
            existing.setStatus(sysDict.getStatus());
        }
        if (sysDict.getRemark() != null) {
            existing.setRemark(sysDict.getRemark());
        }

        SysDict saved = sysDictRepository.save(existing);
        log.info("Updated dict: {} - {}", saved.getCategory(), saved.getDictKey());
        return saved;
    }

    @Transactional
    public void deleteDict(Long id) {
        if (!sysDictRepository.existsById(id)) {
            throw new IllegalArgumentException("字典项不存在: " + id);
        }
        sysDictRepository.deleteById(id);
        log.info("Deleted dict with id: {}", id);
    }

    @Transactional
    public void batchCreateDicts(List<SysDict> dicts) {
        for (SysDict dict : dicts) {
            if (!sysDictRepository.existsByCategoryAndDictKey(dict.getCategory(), dict.getDictKey())) {
                sysDictRepository.save(dict);
            }
        }
        log.info("Batch created {} dict items", dicts.size());
    }
}
