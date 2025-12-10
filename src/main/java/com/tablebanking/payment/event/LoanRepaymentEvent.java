package com.tablebanking.payment.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event to notify main app of loan repayment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanRepaymentEvent {
    
    private String eventId;
    private String eventType;  // LOAN_REPAYMENT
    
    private UUID loanId;
    private UUID memberId;
    private String memberName;
    private UUID groupId;
    private BigDecimal amount;
    private String paymentReference;
    private String paymentMode;
    
    private Instant timestamp;
}
