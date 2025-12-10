package com.tablebanking.payment.repository;

import com.tablebanking.payment.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    
    List<PaymentAllocation> findByPaymentTransactionId(UUID paymentTransactionId);
    
    List<PaymentAllocation> findByTargetIdAndTargetType(UUID targetId, String targetType);
    
    @Query("SELECT a FROM PaymentAllocation a WHERE a.status = 'PENDING'")
    List<PaymentAllocation> findPendingAllocations();
}
