package com.tablebanking.payment.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when disbursement is initiated/completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementEvent {
    
    private String eventId;
    private String eventType;  // DISBURSEMENT_INITIATED, DISBURSEMENT_COMPLETED, DISBURSEMENT_FAILED
    
    private UUID disbursementId;
    private String batchRef;
    private String paymentRef;
    private String externalRef;
    
    private UUID memberId;
    private String memberName;
    private UUID groupId;
    
    private String sourceType;
    private UUID sourceId;
    
    private BigDecimal amount;
    private String currency;
    private String status;
    private String statusDescription;
    
    private Instant timestamp;
}
