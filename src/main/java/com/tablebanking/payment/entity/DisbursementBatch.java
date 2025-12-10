package com.tablebanking.payment.entity;

import com.tablebanking.payment.entity.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch of disbursements to send to Family Bank
 */
@Entity
@Table(name = "disbursement_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementBatch extends BaseEntity {
    
    @Column(name = "batch_ref", nullable = false, unique = true, length = 100)
    private String batchRef;
    
    @Column(name = "account_dr", nullable = false, length = 50)
    private String accountDr;
    
    @Column(name = "narration", columnDefinition = "TEXT")
    private String narration;
    
    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;
    
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";
    
    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "transaction_count")
    @Builder.Default
    private Integer transactionCount = 0;
    
    @Column(name = "cbs_ref", length = 100)
    private String cbsRef;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private BatchStatus status = BatchStatus.PENDING;
    
    @Column(name = "status_description", columnDefinition = "TEXT")
    private String statusDescription;
    
    @Column(name = "submitted_at")
    private Instant submittedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Disbursement> disbursements = new ArrayList<>();
}
