package com.tablebanking.payment.repository;

import com.tablebanking.payment.entity.DisbursementBatch;
import com.tablebanking.payment.entity.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisbursementBatchRepository extends JpaRepository<DisbursementBatch, UUID> {
    
    Optional<DisbursementBatch> findByBatchRef(String batchRef);
    
    boolean existsByBatchRef(String batchRef);
    
    List<DisbursementBatch> findByStatus(BatchStatus status);
    
    @Query("SELECT b FROM DisbursementBatch b WHERE b.status IN ('PENDING', 'QUEUED', 'CBS_POSTED', 'CBS_ACK')")
    List<DisbursementBatch> findPendingBatches();
    
    @Query("SELECT b FROM DisbursementBatch b LEFT JOIN FETCH b.disbursements WHERE b.id = :id")
    Optional<DisbursementBatch> findByIdWithDisbursements(@Param("id") UUID id);
    
    @Query("SELECT b FROM DisbursementBatch b LEFT JOIN FETCH b.disbursements WHERE b.batchRef = :batchRef")
    Optional<DisbursementBatch> findByBatchRefWithDisbursements(@Param("batchRef") String batchRef);
}
