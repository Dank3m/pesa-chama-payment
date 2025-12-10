package com.tablebanking.payment.entity;

import com.tablebanking.payment.entity.enums.DisbursementPurpose;
import com.tablebanking.payment.entity.enums.DisbursementSourceType;
import com.tablebanking.payment.entity.enums.DisbursementStatus;
import com.tablebanking.payment.entity.enums.DisbursementType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Individual disbursement within a batch
 */
@Entity
@Table(name = "disbursements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disbursement extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private DisbursementBatch batch;
    
    @Column(name = "payment_ref", nullable = false, unique = true, length = 100)
    private String paymentRef;
    
    @Column(name = "batch_ref", nullable = false, length = 100)
    private String batchRef;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 50)
    private DisbursementType paymentType;
    
    // Source tracking
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private DisbursementSourceType sourceType;
    
    @Column(name = "source_id")
    private UUID sourceId;
    
    @Column(name = "member_id", nullable = false)
    private UUID memberId;
    
    @Column(name = "group_id")
    private UUID groupId;
    
    // Sender details
    @Column(name = "sender_account", length = 100)
    private String senderAccount;
    
    @Column(name = "sender_bank", length = 100)
    private String senderBank;
    
    @Column(name = "sender_bank_branch", length = 100)
    private String senderBankBranch;
    
    @Column(name = "sender_details", columnDefinition = "TEXT")
    private String senderDetails;
    
    // Beneficiary details
    @Column(name = "beneficiary_account", nullable = false, length = 100)
    private String beneficiaryAccount;
    
    @Column(name = "beneficiary_bank", length = 100)
    private String beneficiaryBank;
    
    @Column(name = "beneficiary_bank_branch", length = 100)
    private String beneficiaryBankBranch;
    
    @Column(name = "beneficiary_name", nullable = false, length = 200)
    private String beneficiaryName;
    
    @Column(name = "beneficiary_phone", length = 20)
    private String beneficiaryPhone;
    
    @Column(name = "beneficiary_details", columnDefinition = "TEXT")
    private String beneficiaryDetails;
    
    // Transaction details
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "KES";
    
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 50)
    private DisbursementPurpose purpose;
    
    // Bank response
    @Column(name = "external_ref", length = 100)
    private String externalRef;
    
    @Column(name = "cbs_ref", length = 100)
    private String cbsRef;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private DisbursementStatus status = DisbursementStatus.PENDING;
    
    @Column(name = "status_description", columnDefinition = "TEXT")
    private String statusDescription;
    
    @Column(name = "completed_at")
    private Instant completedAt;
}
