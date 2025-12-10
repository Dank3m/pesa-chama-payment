package com.tablebanking.payment.repository;

import com.tablebanking.payment.entity.Disbursement;
import com.tablebanking.payment.entity.enums.DisbursementSourceType;
import com.tablebanking.payment.entity.enums.DisbursementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {
    
    Optional<Disbursement> findByPaymentRef(String paymentRef);
    
    boolean existsByPaymentRef(String paymentRef);
    
    List<Disbursement> findByBatchId(UUID batchId);
    
    List<Disbursement> findByBatchRef(String batchRef);
    
    List<Disbursement> findByMemberId(UUID memberId);
    
    Page<Disbursement> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);
    
    List<Disbursement> findByStatus(DisbursementStatus status);
    
    List<Disbursement> findBySourceTypeAndSourceId(DisbursementSourceType sourceType, UUID sourceId);
    
    @Query("SELECT d FROM Disbursement d WHERE d.status IN ('PENDING', 'QUEUED', 'CBS_POSTED', 'CBS_ACK', 'THIRDPARTY_POSTED', 'THIRDPARTY_ACK')")
    List<Disbursement> findPendingDisbursements();
    
    @Query("SELECT COUNT(d) FROM Disbursement d WHERE d.status = :status")
    long countByStatus(@Param("status") DisbursementStatus status);
}
