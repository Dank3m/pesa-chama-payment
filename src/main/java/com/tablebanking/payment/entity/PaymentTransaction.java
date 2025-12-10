package com.tablebanking.payment.entity;

import com.tablebanking.payment.entity.enums.IdentifierType;
import com.tablebanking.payment.entity.enums.PaymentMode;
import com.tablebanking.payment.entity.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payment received via IPN from Family Bank
 */
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction extends BaseEntity {
    
    // Bank transaction details
    @Column(name = "txn_reference", nullable = false, unique = true, length = 100)
    private String txnReference;
    
    @Column(name = "collection_account", nullable = false, length = 50)
    private String collectionAccount;
    
    // Payer details
    @Column(name = "payer_identifier", nullable = false, length = 100)
    private String payerIdentifier;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payer_identifier_type", nullable = false, length = 50)
    private IdentifierType payerIdentifierType;
    
    @Column(name = "payer_name", length = 200)
    private String payerName;
    
    @Column(name = "payer_phone", length = 20)
    private String payerPhone;
    
    // Member mapping (after validation)
    @Column(name = "member_id")
    private UUID memberId;
    
    @Column(name = "member_name", length = 200)
    private String memberName;
    
    @Column(name = "group_id")
    private UUID groupId;
    
    // Transaction details
    @Column(name = "txn_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal txnAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 50)
    private PaymentMode paymentMode;
    
    @Column(name = "txn_narration", columnDefinition = "TEXT")
    private String txnNarration;
    
    @Column(name = "txn_date_time", nullable = false)
    private Instant txnDateTime;
    
    // Processing status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.RECEIVED;
    
    @Column(name = "status_description", columnDefinition = "TEXT")
    private String statusDescription;
    
    // Allocation tracking
    @Column(name = "amount_to_contribution", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountToContribution = BigDecimal.ZERO;
    
    @Column(name = "amount_to_loan", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountToLoan = BigDecimal.ZERO;
    
    @Column(name = "amount_unallocated", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountUnallocated = BigDecimal.ZERO;
    
    @Column(name = "contribution_id")
    private UUID contributionId;
    
    @Column(name = "loan_id")
    private UUID loanId;
    
    // Our acknowledgment reference
    @Column(name = "payment_ref", length = 100)
    private String paymentRef;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @OneToMany(mappedBy = "paymentTransaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentAllocation> allocations = new ArrayList<>();
}
