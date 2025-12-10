package com.tablebanking.payment.entity;

import com.tablebanking.payment.entity.enums.AllocationStatus;
import com.tablebanking.payment.entity.enums.AllocationType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * How a payment was allocated
 */
@Entity
@Table(name = "payment_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAllocation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id", nullable = false)
    private PaymentTransaction paymentTransaction;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_type", nullable = false, length = 50)
    private AllocationType allocationType;
    
    @Column(name = "allocation_order", nullable = false)
    private Integer allocationOrder;
    
    @Column(name = "target_id")
    private UUID targetId;
    
    @Column(name = "target_type", length = 50)
    private String targetType;
    
    @Column(name = "allocated_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocatedAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private AllocationStatus status = AllocationStatus.PENDING;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
