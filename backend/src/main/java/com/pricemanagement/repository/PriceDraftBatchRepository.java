package com.pricemanagement.repository;

import com.pricemanagement.entity.PriceDraftBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceDraftBatchRepository extends JpaRepository<PriceDraftBatch, Long> {

    List<PriceDraftBatch> findByEffectiveDateOrderByCreatedTimeDesc(LocalDate effectiveDate);

    Optional<PriceDraftBatch> findFirstByEffectiveDateAndStatusInOrderByCreatedTimeDesc(
            LocalDate effectiveDate,
            Collection<PriceDraftBatch.DraftStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PriceDraftBatch b WHERE b.id = :id")
    Optional<PriceDraftBatch> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PriceDraftBatch b WHERE b.effectiveDate = :effectiveDate AND b.status IN :statuses ORDER BY b.createdTime DESC LIMIT 1")
    Optional<PriceDraftBatch> findActiveByDateForUpdate(
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("statuses") Collection<PriceDraftBatch.DraftStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PriceDraftBatch b WHERE b.effectiveDate = :effectiveDate AND b.status IN :statuses ORDER BY b.createdTime ASC, b.id ASC")
    List<PriceDraftBatch> findAllByDateAndStatusForUpdate(
            @Param("effectiveDate") LocalDate effectiveDate,
            @Param("statuses") Collection<PriceDraftBatch.DraftStatus> statuses);

    @Query("SELECT b FROM PriceDraftBatch b WHERE b.status IN :statuses ORDER BY b.effectiveDate ASC, b.createdTime ASC, b.id ASC")
    List<PriceDraftBatch> findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
            @Param("statuses") Collection<PriceDraftBatch.DraftStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PriceDraftBatch b WHERE b.status IN :statuses ORDER BY b.effectiveDate ASC, b.createdTime ASC, b.id ASC")
    List<PriceDraftBatch> findAllByStatusForUpdate(
            @Param("statuses") Collection<PriceDraftBatch.DraftStatus> statuses);
}
