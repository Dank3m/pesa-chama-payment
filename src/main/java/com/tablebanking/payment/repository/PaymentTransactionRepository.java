package com.tablebanking.payment.repository;

import com.tablebanking.payment.entity.PaymentTransaction;
import com.tablebanking.payment.entity.enums.IdentifierType;
import com.tablebanking.payment.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    
    Optional<PaymentTransaction> findByTxnReference(String txnReference);
    
    Optional<PaymentTransaction> findByPaymentRef(String paymentRef);
    
    boolean existsByTxnReference(String txnReference);
    
    List<PaymentTransaction> findByMemberId(UUID memberId);
    
    Page<PaymentTransaction> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);
    
    List<PaymentTransaction> findByStatus(PaymentStatus status);
    
    @Query("SELECT p FROM PaymentTransaction p WHERE p.status = :status AND p.createdAt < :cutoff")
    List<PaymentTransaction> findStaleTransactions(
            @Param("status") PaymentStatus status, 
            @Param("cutoff") Instant cutoff);
    
    @Query("SELECT p FROM PaymentTransaction p WHERE p.payerIdentifier = :identifier AND p.payerIdentifierType = :type")
    List<PaymentTransaction> findByPayerIdentifierAndType(
            @Param("identifier") String identifier, 
            @Param("type") IdentifierType type);
    
    @Query("SELECT COUNT(p) FROM PaymentTransaction p WHERE p.status = :status")
    long countByStatus(@Param("status") PaymentStatus status);
    
    @Query("SELECT SUM(p.txnAmount) FROM PaymentTransaction p WHERE p.memberId = :memberId AND p.status = 'COMPLETED'")
    BigDecimal getTotalCompletedByMember(@Param("memberId") UUID memberId);
}
