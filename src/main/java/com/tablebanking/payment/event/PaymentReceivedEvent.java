package com.tablebanking.payment.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when payment is received and allocated
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReceivedEvent {
    
    private String eventId;
    private String eventType;  // PAYMENT_RECEIVED, PAYMENT_ALLOCATED
    
    // Payment details
    private UUID paymentTransactionId;
    private String txnReference;
    private BigDecimal amount;
    private String paymentMode;
    
    // Member details
    private UUID memberId;
    private String memberName;
    private UUID groupId;
    
    // Allocation details
    private BigDecimal allocatedToContribution;
    private BigDecimal allocatedToLoan;
    private BigDecimal unallocatedAmount;
    private UUID contributionId;
    private UUID loanId;
    
    private Instant timestamp;
}
