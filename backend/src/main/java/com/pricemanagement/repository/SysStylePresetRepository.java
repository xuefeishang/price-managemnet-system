package com.pricemanagement.repository;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysStylePreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysStylePresetRepository extends JpaRepository<SysStylePreset, Long> {

    List<SysStylePreset> findByPresetTypeOrderBySortOrderAsc(String presetType);

    List<SysStylePreset> findByPresetTypeAndStatusOrderBySortOrderAsc(String presetType, CommonStatus status);

    Optional<SysStylePreset> findByPresetTypeAndPresetKey(String presetType, String presetKey);

    Optional<SysStylePreset> findByPresetTypeAndIsDefaultTrue(String presetType);

    boolean existsByPresetTypeAndPresetKey(String presetType, String presetKey);

    void deleteByPresetType(String presetType);

}
